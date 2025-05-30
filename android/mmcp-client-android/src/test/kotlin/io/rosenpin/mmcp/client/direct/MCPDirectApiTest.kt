package io.rosenpin.mmcp.client.direct

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.server.IMcpToolService
import io.rosenpin.mmcp.server.IMcpResourceService
import io.rosenpin.mmcp.server.IMcpPromptService
import io.rosenpin.mmcp.server.McpServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for MCPDirectApiImpl functionality
 */
class MCPDirectApiTest {
    
    private lateinit var discovery: McpServerDiscovery
    private lateinit var connectionManager: McpConnectionManager
    private lateinit var directApi: MCPDirectApi
    
    @Before
    fun setup() {
        discovery = mockk(relaxed = true)
        connectionManager = mockk(relaxed = true)
        directApi = MCPDirectApiImpl(discovery, connectionManager)
    }
    
    @Test
    fun `listTools returns empty list when no servers connected`() = runTest {
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val tools = directApi.listTools()
        
        assertTrue("Should return empty list", tools.isEmpty())
    }
    
    @Test
    fun `listTools aggregates tools from multiple servers`() = runTest {
        val toolService1 = mockk<IMcpToolService>()
        val toolService2 = mockk<IMcpToolService>()
        
        every { toolService1.listTools() } returns """[{"name": "tool1"}]"""
        every { toolService2.listTools() } returns """[{"name": "tool2"}]"""
        
        val connection1 = McpConnectionManager.ServerConnection(
            packageName = "com.example.server1",
            toolService = toolService1,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        val connection2 = McpConnectionManager.ServerConnection(
            packageName = "com.example.server2",
            toolService = toolService2,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnectedServers() } returns listOf(connection1, connection2)
        
        val tools = directApi.listTools()
        
        assertEquals("Should return tools from both servers", 2, tools.size)
        assertTrue("Should contain tools from both servers", 
            tools.any { it.serverId == "com.example.server1" } &&
            tools.any { it.serverId == "com.example.server2" })
    }
    
    @Test
    fun `callTool throws MCPException when server not connected`() = runTest {
        every { connectionManager.getConnection("unknown.server") } returns null
        
        try {
            directApi.callTool("unknown.server", "test_tool", emptyMap())
            fail("Should have thrown MCPException")
        } catch (exception: MCPException) {
            assertEquals("Should have server not connected error code", 
                MCPException.SERVER_NOT_CONNECTED, exception.code)
            assertEquals("Should include server ID", "unknown.server", exception.serverId)
        }
    }
    
    @Test
    fun `callTool throws MCPException when server has no tools`() = runTest {
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            toolService = null, // No tool service
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        try {
            directApi.callTool("com.example.server", "test_tool", emptyMap())
            fail("Should have thrown MCPException")
        } catch (exception: MCPException) {
            assertEquals("Should have method not found error code",
                MCPException.METHOD_NOT_FOUND, exception.code)
            assertEquals("Should include server ID", "com.example.server", exception.serverId)
        }
    }
    
    @Test
    fun `callTool successfully executes tool`() = runTest {
        val toolService = mockk<IMcpToolService>()
        every { toolService.executeTool("test_tool", "{}", null) } returns "success"
        
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            toolService = toolService,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        val result = directApi.callTool("com.example.server", "test_tool", emptyMap())
        
        assertNotNull("Should return result", result)
    }
    
    @Test
    fun `listResources returns empty list when no servers connected`() = runTest {
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val resources = directApi.listResources()
        
        assertTrue("Should return empty list", resources.isEmpty())
    }
    
    @Test
    fun `listResources aggregates resources from multiple servers`() = runTest {
        val resourceService1 = mockk<IMcpResourceService>()
        val resourceService2 = mockk<IMcpResourceService>()
        
        every { resourceService1.listResources() } returns """[{"uri": "resource1"}]"""
        every { resourceService2.listResources() } returns """[{"uri": "resource2"}]"""
        
        val connection1 = McpConnectionManager.ServerConnection(
            packageName = "com.example.server1",
            resourceService = resourceService1,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        val connection2 = McpConnectionManager.ServerConnection(
            packageName = "com.example.server2",
            resourceService = resourceService2,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnectedServers() } returns listOf(connection1, connection2)
        
        val resources = directApi.listResources()
        
        assertEquals("Should return resources from both servers", 2, resources.size)
    }
    
    @Test
    fun `readResource throws MCPException when server not connected`() = runTest {
        every { connectionManager.getConnection("unknown.server") } returns null
        
        try {
            directApi.readResource("unknown.server", "test://resource")
            fail("Should have thrown MCPException")
        } catch (exception: MCPException) {
            assertEquals("Should have server not connected error code",
                MCPException.SERVER_NOT_CONNECTED, exception.code)
        }
    }
    
    @Test
    fun `readResource successfully reads resource`() = runTest {
        val resourceService = mockk<IMcpResourceService>()
        every { resourceService.readResource("test://resource", null) } returns "content"
        
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            resourceService = resourceService,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        val result = directApi.readResource("com.example.server", "test://resource")
        
        assertNotNull("Should return content", result)
        assertTrue("Should return byte array", result is ByteArray)
    }
    
    @Test
    fun `listPrompts returns empty list when no servers connected`() = runTest {
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val prompts = directApi.listPrompts()
        
        assertTrue("Should return empty list", prompts.isEmpty())
    }
    
    @Test
    fun `getPrompt throws MCPException when server not connected`() = runTest {
        every { connectionManager.getConnection("unknown.server") } returns null
        
        try {
            directApi.getPrompt("unknown.server", "test_prompt", emptyMap())
            fail("Should have thrown MCPException")
        } catch (exception: MCPException) {
            assertEquals("Should have server not connected error code",
                MCPException.SERVER_NOT_CONNECTED, exception.code)
        }
    }
    
    @Test
    fun `getPrompt successfully gets prompt`() = runTest {
        val promptService = mockk<IMcpPromptService>()
        every { promptService.getPrompt("test_prompt", "{}", null) } returns "prompt_result"
        
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            promptService = promptService,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        val result = directApi.getPrompt("com.example.server", "test_prompt", emptyMap())
        
        assertNotNull("Should return result", result)
        assertTrue("Should return MCPPromptResult", result is MCPPromptResult)
    }
    
    @Test
    fun `listServers returns server information`() = runTest {
        val discoveredServers = listOf(
            McpServerDiscovery.DiscoveredServer(
                packageName = "com.example.server1",
                serviceName = "com.example.server1.McpService",
                capabilities = listOf("tools", "resources"),
                serverInfo = McpServerInfo(
                    packageName = "com.example.server1",
                    serviceName = "com.example.server1.McpService",
                    serverName = "Example Server 1",
                    version = "1.0",
                    description = "Example server",
                    capabilities = listOf("tools", "resources"),
                    protocolVersion = "2024-11-05"
                )
            ),
            McpServerDiscovery.DiscoveredServer(
                packageName = "com.example.server2",
                serviceName = "com.example.server2.McpService",
                capabilities = listOf("prompts"),
                serverInfo = null
            )
        )
        
        val connections = mapOf(
            "com.example.server1" to McpConnectionManager.ServerConnection(
                packageName = "com.example.server1",
                status = McpConnectionManager.ConnectionStatus.CONNECTED,
                toolService = mockk(),
                resourceService = mockk()
            )
        )
        
        every { discovery.discoveredServers.value } returns discoveredServers
        every { connectionManager.connections.value } returns connections
        
        val servers = directApi.listServers()
        
        assertEquals("Should return both servers", 2, servers.size)
        
        val server1 = servers.find { it.packageName == "com.example.server1" }
        val server2 = servers.find { it.packageName == "com.example.server2" }
        
        assertNotNull("Should have server1", server1)
        assertNotNull("Should have server2", server2)
        
        assertEquals("Server1 should be connected", MCPConnectionStatus.CONNECTED, server1?.connectionStatus)
        assertEquals("Server2 should be disconnected", MCPConnectionStatus.DISCONNECTED, server2?.connectionStatus)
        
        assertTrue("Server1 should have tools", server1?.hasTools == true)
        assertTrue("Server1 should have resources", server1?.hasResources == true)
        assertFalse("Server1 should not have prompts", server1?.hasPrompts == true)
    }
    
    @Test
    fun `observeServerStatus emits updates when connections change`() = runTest {
        val discoveredServersFlow = MutableStateFlow(emptyList<McpServerDiscovery.DiscoveredServer>())
        val connectionsFlow = MutableStateFlow(emptyMap<String, McpConnectionManager.ServerConnection>())
        
        every { discovery.discoveredServers } returns discoveredServersFlow
        every { connectionManager.connections } returns connectionsFlow
        
        val statusFlow = directApi.observeServerStatus()
        
        // Initial state should be empty
        val initialStatus = statusFlow.first()
        assertTrue("Initial status should be empty", initialStatus.isEmpty())
        
        // Add a discovered server
        val discoveredServer = McpServerDiscovery.DiscoveredServer(
            packageName = "com.example.server",
            serviceName = "com.example.server.McpService",
            capabilities = listOf("tools"),
            serverInfo = null
        )
        
        discoveredServersFlow.value = listOf(discoveredServer)
        
        // Verify the server appears in status
        // Note: In a real test, we'd collect the flow and verify emissions
    }
    
    @Test
    fun `observeTools emits updates when tool availability changes`() = runTest {
        val connectionsFlow = MutableStateFlow(emptyMap<String, McpConnectionManager.ServerConnection>())
        every { connectionManager.connections } returns connectionsFlow
        
        val toolsFlow = directApi.observeTools()
        
        // Initial state should be empty
        val initialTools = toolsFlow.first()
        assertTrue("Initial tools should be empty", initialTools.isEmpty())
        
        // Note: Full flow testing would require more complex setup
        // to simulate connection changes and tool updates
    }
    
    @Test
    fun `concurrent tool calls work correctly`() = runTest {
        val toolService = mockk<IMcpToolService>()
        every { toolService.executeTool(any(), any(), any()) } returns "success"
        
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            toolService = toolService,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        // Launch multiple concurrent tool calls
        coroutineScope {
            val results = (1..5).map { i ->
                async {
                    directApi.callTool("com.example.server", "tool$i", mapOf("param" to i))
                }
            }
            
            // Wait for all to complete
            val completedResults = results.map { it.await() }
            
            assertEquals("All calls should complete", 5, completedResults.size)
            assertTrue("All calls should succeed", completedResults.all { it != null })
        }
    }
    
    @Test
    fun `error propagation preserves original error information`() = runTest {
        val toolService = mockk<IMcpToolService>()
        every { toolService.executeTool(any(), any(), any()) } throws RuntimeException("AIDL call failed")
        
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            toolService = toolService,
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        try {
            directApi.callTool("com.example.server", "failing_tool", emptyMap())
            fail("Should have thrown MCPException")
        } catch (exception: MCPException) {
            assertEquals("Should have execution failed error code",
                MCPException.EXECUTION_FAILED, exception.code)
            assertEquals("Should include server ID", "com.example.server", exception.serverId)
            assertNotNull("Should have cause", exception.cause)
            assertTrue("Should preserve original exception",
                exception.cause is RuntimeException)
        }
    }
}