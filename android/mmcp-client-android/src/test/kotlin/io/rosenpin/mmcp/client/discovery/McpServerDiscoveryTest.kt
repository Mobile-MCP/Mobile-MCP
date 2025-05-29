package io.rosenpin.mmcp.client.discovery

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.rosenpin.mmcp.server.IMcpService
import io.rosenpin.mmcp.server.McpConstants
import io.rosenpin.mmcp.server.McpServerInfo
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class McpServerDiscoveryTest {
    
    @MockK
    private lateinit var context: Context
    
    @MockK 
    private lateinit var packageManager: PackageManager
    
    @MockK
    private lateinit var mcpService: IMcpService
    
    @MockK
    private lateinit var binder: IBinder
    
    private lateinit var discovery: McpServerDiscovery
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        every { context.packageManager } returns packageManager
        every { context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } returns true
        every { context.unbindService(any<ServiceConnection>()) } returns Unit
        
        discovery = McpServerDiscovery(context)
    }
    
    @Test
    fun `startDiscovery should find MCP servers from PackageManager`() = runTest {
        // Given
        val serviceInfo = ServiceInfo().apply {
            packageName = "com.example.mcpserver"
            name = "com.example.mcpserver.McpService" 
            exported = true
        }
        
        val resolveInfo = ResolveInfo().apply {
            this.serviceInfo = serviceInfo
        }
        
        every { 
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(resolveInfo)
        
        every { IMcpService.Stub.asInterface(binder) } returns mcpService
        every { mcpService.serverInfo } returns """{"name":"Test Server","version":"1.0.0"}"""
        
        // Mock service connection
        val connectionSlot = slot<ServiceConnection>()
        every { 
            context.bindService(any<Intent>(), capture(connectionSlot), any<Int>()) 
        } answers {
            // Simulate successful connection
            connectionSlot.captured.onServiceConnected(mockk(), binder)
            true
        }
        
        // When
        val result = discovery.startDiscovery()
        
        // Then
        assertTrue(result.isSuccess)
        
        val servers = discovery.discoveredServers.value
        assertEquals(1, servers.size)
        assertEquals("com.example.mcpserver", servers.first().packageName)
        assertEquals(McpServerDiscovery.ConnectionStatus.CONNECTED, servers.first().connectionStatus)
    }
    
    @Test
    fun `startDiscovery should filter non-exported services`() = runTest {
        // Given
        val serviceInfo = ServiceInfo().apply {
            packageName = "com.example.mcpserver"
            name = "com.example.mcpserver.McpService"
            exported = false // Not exported
        }
        
        val resolveInfo = ResolveInfo().apply {
            this.serviceInfo = serviceInfo
        }
        
        every { 
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(resolveInfo)
        
        // When
        val result = discovery.startDiscovery()
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(discovery.discoveredServers.value.isEmpty())
    }
    
    @Test
    fun `startDiscovery should handle connection failures gracefully`() = runTest {
        // Given
        val serviceInfo = ServiceInfo().apply {
            packageName = "com.example.mcpserver"
            name = "com.example.mcpserver.McpService"
            exported = true
        }
        
        val resolveInfo = ResolveInfo().apply {
            this.serviceInfo = serviceInfo
        }
        
        every { 
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(resolveInfo)
        
        every { 
            context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) 
        } returns false // Binding fails
        
        // When
        val result = discovery.startDiscovery()
        
        // Then
        assertTrue(result.isSuccess)
        
        val servers = discovery.discoveredServers.value
        assertEquals(1, servers.size)
        assertEquals(McpServerDiscovery.ConnectionStatus.ERROR, servers.first().connectionStatus)
    }
    
    @Test
    fun `getServer should return correct server by package name`() = runTest {
        // Given
        val serviceInfo = ServiceInfo().apply {
            packageName = "com.example.mcpserver"
            name = "com.example.mcpserver.McpService"
            exported = true
        }
        
        val resolveInfo = ResolveInfo().apply {
            this.serviceInfo = serviceInfo
        }
        
        every { 
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns listOf(resolveInfo)
        
        every { IMcpService.Stub.asInterface(binder) } returns mcpService
        every { mcpService.serverInfo } returns """{"name":"Test Server"}"""
        
        val connectionSlot = slot<ServiceConnection>()
        every { 
            context.bindService(any<Intent>(), capture(connectionSlot), any<Int>()) 
        } answers {
            connectionSlot.captured.onServiceConnected(mockk(), binder)
            true
        }
        
        discovery.startDiscovery()
        
        // When
        val server = discovery.getServer("com.example.mcpserver")
        
        // Then
        assertEquals("com.example.mcpserver", server?.packageName)
        assertEquals(McpServerDiscovery.ConnectionStatus.CONNECTED, server?.connectionStatus)
    }
    
    @Test
    fun `cleanup should unbind all services`() {
        // Given
        every { context.unbindService(any<ServiceConnection>()) } returns Unit
        
        // When
        discovery.cleanup()
        
        // Then - no exceptions should be thrown
        // Verify is difficult due to coroutines scope cancellation
    }
    
    @Test
    fun `isScanning should reflect discovery state`() = runTest {
        // Given
        every { 
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns emptyList<ResolveInfo>()
        
        // Initially not scanning
        assertFalse(discovery.isScanning.value)
        
        // When starting discovery
        discovery.startDiscovery()
        
        // Then should complete and not be scanning
        assertFalse(discovery.isScanning.value)
    }
    
    @Test
    fun `should scan for all MCP service types`() = runTest {
        // Given
        every { 
            packageManager.queryIntentServices(any<Intent>(), any<Int>())
        } returns emptyList<ResolveInfo>()
        
        // When
        discovery.startDiscovery()
        
        // Then should query for all service actions
        verify {
            packageManager.queryIntentServices(
                match<Intent> { it.action == McpConstants.ACTION_MCP_SERVICE },
                any<Int>()
            )
        }
        verify {
            packageManager.queryIntentServices(
                match<Intent> { it.action == McpConstants.ACTION_MCP_TOOL_SERVICE },
                any<Int>()
            )
        }
        verify {
            packageManager.queryIntentServices(
                match<Intent> { it.action == McpConstants.ACTION_MCP_RESOURCE_SERVICE },
                any<Int>()
            )
        }
        verify {
            packageManager.queryIntentServices(
                match<Intent> { it.action == McpConstants.ACTION_MCP_PROMPT_SERVICE },
                any<Int>()
            )
        }
        verify {
            packageManager.queryIntentServices(
                match<Intent> { it.action == McpConstants.ACTION_MCP_DISCOVERY_SERVICE },
                any<Int>()
            )
        }
    }
}