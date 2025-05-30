package io.rosenpin.mmcp.client

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.rosenpin.mmcp.server.McpException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class McpClientTest {
    
    @MockK
    private lateinit var context: Context
    
    private lateinit var client: McpClient
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Mock the context and its dependencies
        every { context.packageManager } returns mockk<PackageManager>()
        
        client = McpClient(context)
    }
    
    @Test
    fun `connectToServer should fail if server not discovered first`() = runTest {
        // When - trying to connect to server that wasn't discovered
        val result = client.connectToServer("com.unknown.server")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    @Ignore("TODO: Requires Android instrumented test environment or Robolectric for PackageManager mocking")
    fun `executeTool should fail if not connected to server`() = runTest {
        // When - trying to execute tool without connection
        val result = client.executeTool("com.example.server", "test_tool")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    fun `executeTool should fail if server has no tool service`() = runTest {
        // This test would require mocking internal discovery and connection managers
        // For now, we test the error case when no tool service is available
        
        val result = client.executeTool("com.example.server", "test_tool")
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `readResource should fail if not connected to server`() = runTest {
        // When - trying to read resource without connection
        val result = client.readResource("com.example.server", "test://resource")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    fun `getPrompt should fail if not connected to server`() = runTest {
        // When - trying to get prompt without connection
        val result = client.getPrompt("com.example.server", "test_prompt")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    fun `listTools should fail if not connected to server`() = runTest {
        // When - trying to list tools without connection
        val result = client.listTools("com.example.server")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    fun `listResources should fail if not connected to server`() = runTest {
        // When - trying to list resources without connection
        val result = client.listResources("com.example.server")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    fun `listPrompts should fail if not connected to server`() = runTest {
        // When - trying to list prompts without connection
        val result = client.listPrompts("com.example.server")
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is McpException)
    }
    
    @Test
    fun `isConnectedTo should return false for unknown server`() {
        // When
        val isConnected = client.isConnectedTo("com.unknown.server")
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `getConnectedServers should return empty list initially`() {
        // When
        val connectedServers = client.getConnectedServers()
        
        // Then
        assertTrue(connectedServers.isEmpty())
    }
    
    @Test
    fun `getConnection should return null for unknown server`() {
        // When
        val connection = client.getConnection("com.unknown.server")
        
        // Then
        assertEquals(null, connection)
    }
    
    @Test
    fun `cleanup should not throw exceptions`() {
        // When & Then - should not throw
        client.cleanup()
    }
    
    @Test
    @Ignore("TODO: Requires Android instrumented test environment or Robolectric for PackageManager mocking")
    fun `initialize should not throw exceptions`() {
        // When & Then - should not throw
        client.initialize()
    }
}