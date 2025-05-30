package io.rosenpin.mmcp.client.http

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.server.IMcpToolService
import io.rosenpin.mmcp.server.IMcpResourceService
import io.rosenpin.mmcp.server.IMcpPromptService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for MCPRequestHandler functionality
 */
class MCPRequestHandlerTest {
    
    private lateinit var discovery: McpServerDiscovery
    private lateinit var connectionManager: McpConnectionManager
    private lateinit var requestHandler: MCPRequestHandler
    
    @Before
    fun setup() {
        discovery = mockk(relaxed = true)
        connectionManager = mockk(relaxed = true)
        requestHandler = MCPRequestHandler(discovery, connectionManager)
    }
    
    @Test
    fun `handleToolsListSync returns valid JSON-RPC response with no servers`() {
        // Mock no connected servers
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val response = requestHandler.handleToolsListSync()
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"result\""))
        assertTrue(response.contains("\"tools\""))
        assertTrue(response.contains("\"id\""))
    }
    
    @Test
    fun `handleToolsListSync aggregates tools from multiple servers`() {
        // Mock connected servers with tools
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
        
        val response = requestHandler.handleToolsListSync()
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"result\""))
        assertTrue(response.contains("\"tools\""))
        assertTrue(response.contains("example_tool")) // From mock implementation
    }
    
    @Test
    fun `handleToolCallSync returns error for unknown server`() {
        every { connectionManager.getConnection("unknown.server") } returns null
        
        val requestBody = """{"jsonrpc":"2.0","id":"test-1","method":"tools/call","params":{"serverId":"unknown.server","name":"test_tool"}}"""
        val response = requestHandler.handleToolCallSync(requestBody)
        
        assertTrue("Response should be JSON-RPC 2.0", response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue("Response should contain error", response.contains("\"error\""))
        assertTrue("Should have error code -32602", response.contains("-32602")) // Invalid params
        assertTrue("Should mention not connected", response.contains("not connected"))
    }
    
    @Test
    fun `handleToolCallSync returns error for server without tools`() {
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            toolService = null, // No tool service
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        val requestBody = """{"jsonrpc":"2.0","id":"test-1","method":"tools/call","params":{"serverId":"com.example.server","name":"test_tool"}}"""
        val response = requestHandler.handleToolCallSync(requestBody)
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"error\""))
        assertTrue(response.contains("-32601")) // Method not found
        assertTrue(response.contains("does not support tools"))
    }
    
    @Test
    fun `handleResourcesListSync returns valid JSON-RPC response`() {
        // Mock no connected servers
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val response = requestHandler.handleResourcesListSync()
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"result\""))
        assertTrue(response.contains("\"resources\""))
        assertTrue(response.contains("\"id\""))
    }
    
    @Test
    fun `handleResourceReadSync returns error for unknown server`() {
        every { connectionManager.getConnection("unknown.server") } returns null
        
        val requestBody = """{"jsonrpc":"2.0","id":"test-1","method":"resources/read","params":{"serverId":"unknown.server","uri":"test://resource"}}"""
        val response = requestHandler.handleResourceReadSync(requestBody)
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"error\""))
        assertTrue(response.contains("-32602")) // Invalid params
        assertTrue(response.contains("not connected"))
    }
    
    @Test
    fun `handleResourceReadSync returns error for server without resources`() {
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            resourceService = null, // No resource service
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        val requestBody = """{"jsonrpc":"2.0","id":"test-1","method":"resources/read","params":{"serverId":"com.example.server","uri":"test://resource"}}"""
        val response = requestHandler.handleResourceReadSync(requestBody)
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"error\""))
        assertTrue(response.contains("-32601")) // Method not found
        assertTrue(response.contains("does not support resources"))
    }
    
    @Test
    fun `handlePromptsListSync returns valid JSON-RPC response`() {
        // Mock no connected servers
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val response = requestHandler.handlePromptsListSync()
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"result\""))
        assertTrue(response.contains("\"prompts\""))
        assertTrue(response.contains("\"id\""))
    }
    
    @Test
    fun `handlePromptGetSync returns error for unknown server`() {
        every { connectionManager.getConnection("unknown.server") } returns null
        
        val requestBody = """{"jsonrpc":"2.0","id":"test-1","method":"prompts/get","params":{"serverId":"unknown.server","name":"test_prompt"}}"""
        val response = requestHandler.handlePromptGetSync(requestBody)
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"error\""))
        assertTrue(response.contains("-32602")) // Invalid params
        assertTrue(response.contains("not connected"))
    }
    
    @Test
    fun `handlePromptGetSync returns error for server without prompts`() {
        val connection = McpConnectionManager.ServerConnection(
            packageName = "com.example.server",
            promptService = null, // No prompt service
            status = McpConnectionManager.ConnectionStatus.CONNECTED
        )
        
        every { connectionManager.getConnection("com.example.server") } returns connection
        
        val requestBody = """{"jsonrpc":"2.0","id":"test-1","method":"prompts/get","params":{"serverId":"com.example.server","name":"test_prompt"}}"""
        val response = requestHandler.handlePromptGetSync(requestBody)
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"error\""))
        assertTrue(response.contains("-32601")) // Method not found
        assertTrue(response.contains("does not support prompts"))
    }
    
    @Test
    fun `handleServersListSync returns server information`() {
        // Mock discovered servers
        val discoveredServers = listOf(
            McpServerDiscovery.DiscoveredServer(
                packageName = "com.example.server1",
                serviceName = "com.example.server1.McpService",
                capabilities = listOf("tools", "resources"),
                serverInfo = null
            ),
            McpServerDiscovery.DiscoveredServer(
                packageName = "com.example.server2",
                serviceName = "com.example.server2.McpService",
                capabilities = listOf("prompts"),
                serverInfo = null
            )
        )
        
        // Mock connections
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
        
        val response = requestHandler.handleServersListSync()
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"result\""))
        assertTrue(response.contains("\"servers\""))
        assertTrue(response.contains("com.example.server1"))
        assertTrue(response.contains("com.example.server2"))
        assertTrue(response.contains("CONNECTED"))
        assertTrue(response.contains("DISCONNECTED"))
    }
    
    @Test
    fun `error responses follow JSON-RPC 2 format`() {
        every { connectionManager.getConnection(any()) } returns null
        
        val response = requestHandler.handleToolCallSync("{}")
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"error\""))
        assertTrue(response.contains("\"code\""))
        assertTrue(response.contains("\"message\""))
        assertTrue(response.contains("\"id\""))
    }
    
    @Test
    fun `success responses follow JSON-RPC 2 format`() {
        every { connectionManager.getConnectedServers() } returns emptyList()
        
        val response = requestHandler.handleToolsListSync()
        
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(response.contains("\"result\""))
        assertTrue(response.contains("\"id\""))
        assertFalse(response.contains("\"error\""))
    }
}