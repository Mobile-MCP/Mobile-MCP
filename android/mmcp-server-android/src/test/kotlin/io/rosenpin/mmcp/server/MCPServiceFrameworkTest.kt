package io.rosenpin.mmcp.server

import io.rosenpin.mmcp.server.annotations.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Integration test for the complete MCP Service Framework.
 * 
 * Tests the end-to-end flow from annotation discovery to method execution.
 */
class MCPServiceFrameworkTest {
    
    private lateinit var processor: MCPAnnotationProcessor
    
    @Before
    fun setup() {
        processor = MCPAnnotationProcessor()
    }
    
    @Test
    fun `test annotation discovery and processing`() {
        // Process the test server class
        val serverInfo = processor.processServerClass(TestMCPServer::class.java)
        
        assertNotNull("Server info should not be null", serverInfo)
        assertEquals("Server ID should match", "test.server", serverInfo!!.id)
        assertEquals("Server name should match", "Test Server", serverInfo.name)
        assertEquals("Should have tools capability", true, serverInfo.capabilities.contains("tools"))
        assertEquals("Should have resources capability", true, serverInfo.capabilities.contains("resources"))
        assertEquals("Should have prompts capability", true, serverInfo.capabilities.contains("prompts"))
        assertEquals("Should have 2 tools", 2, serverInfo.tools.size)
        assertEquals("Should have 1 resource", 1, serverInfo.resources.size)
        assertEquals("Should have 1 prompt", 1, serverInfo.prompts.size)
    }
    
    @Test
    fun `test method registry tool execution`() {
        val serverInfo = processor.processServerClass(TestMCPServer::class.java)!!
        val serverInstance = TestMCPServer()
        val registry = MCPMethodRegistry(serverInfo, serverInstance)
        
        // Test synchronous tool execution
        val parametersJson = """{"a": 5, "b": 3}"""
        val result = registry.executeTool("add", parametersJson, null, kotlinx.coroutines.GlobalScope)
        
        assertNotNull("Result should not be null", result)
        // ARCHITECTURAL FIX: Server now returns simple strings, not JSON
        assertEquals("Addition result should be correct", "8.0", result)
    }
    
    @Test
    fun `test method registry resource reading`() {
        val serverInfo = processor.processServerClass(TestMCPServer::class.java)!!
        val serverInstance = TestMCPServer()
        val registry = MCPMethodRegistry(serverInfo, serverInstance)
        
        // Test resource reading
        val result = registry.readResource("test://example", null, kotlinx.coroutines.GlobalScope)
        
        assertNotNull("Result should not be null", result)
        // ARCHITECTURAL FIX: Server now returns simple strings, not JSON
        assertTrue("Should contain test data", result.contains("Test resource data for test://example"))
    }
    
    @Test
    fun `test method registry prompt generation`() {
        val serverInfo = processor.processServerClass(TestMCPServer::class.java)!!
        val serverInstance = TestMCPServer()
        val registry = MCPMethodRegistry(serverInfo, serverInstance)
        
        // Test prompt generation
        val parametersJson = """{"topic": "math"}"""
        val result = registry.getPrompt("test_prompt", parametersJson, null, kotlinx.coroutines.GlobalScope)
        
        assertNotNull("Result should not be null", result)
        // ARCHITECTURAL FIX: Server now returns simple strings, not JSON
        assertTrue("Should contain math topic", result.contains("math"))
    }
    
    @Test
    fun `test parameter validation`() {
        val serverInfo = processor.processServerClass(TestMCPServer::class.java)!!
        val serverInstance = TestMCPServer()
        val registry = MCPMethodRegistry(serverInfo, serverInstance)
        
        // Test with missing required parameter
        val invalidParametersJson = """{"a": 5}""" // Missing required parameter 'b'
        val result = registry.executeTool("add", invalidParametersJson, null, kotlinx.coroutines.GlobalScope)
        
        // ARCHITECTURAL FIX: Server now returns simple error strings, not JSON
        assertTrue("Should return error for missing parameter", result.startsWith("Error:"))
        assertTrue("Should mention parameter validation", result.contains("Parameter validation failed"))
    }
    
    @Test
    fun `test server validation`() {
        // Test valid server
        val validErrors = processor.validateServerClass(TestMCPServer::class.java)
        assertTrue("Valid server should have no errors", validErrors.isEmpty())
        
        // Test invalid server (no annotation)
        val invalidErrors = processor.validateServerClass(InvalidServer::class.java)
        assertFalse("Invalid server should have errors", invalidErrors.isEmpty())
        assertTrue("Should require @MCPServer annotation", 
            invalidErrors.any { it.contains("@MCPServer") })
    }
    
    // ===================================================================================
    // Test Classes
    // ===================================================================================
    
    @MCPServer(
        id = "test.server",
        name = "Test Server", 
        description = "Test MCP server for framework validation",
        version = "1.0.0"
    )
    class TestMCPServer {
        
        @MCPTool(
            id = "add",
            name = "add",
            description = "Add two numbers",
            parameters = """{
                "type": "object",
                "properties": {
                    "a": {"type": "number"},
                    "b": {"type": "number"}
                },
                "required": ["a", "b"]
            }"""
        )
        fun add(@MCPParam("a") a: Double, @MCPParam("b") b: Double): Double {
            return a + b
        }
        
        @MCPTool(
            id = "async_test",
            name = "async_test",
            description = "Test async operation"
        )
        suspend fun asyncTest(): String {
            kotlinx.coroutines.delay(10)
            return "async result"
        }
        
        @MCPResource(
            scheme = "test",
            name = "Test Resource",
            description = "Test resource handler"
        )
        fun getTestResource(@MCPParam("uri") uri: String): String {
            return "Test resource data for $uri"
        }
        
        @MCPPrompt(
            id = "test_prompt",
            name = "test_prompt", 
            description = "Test prompt generator",
            parameters = """{
                "type": "object",
                "properties": {
                    "topic": {"type": "string"}
                }
            }"""
        )
        fun generateTestPrompt(@MCPParam("topic") topic: String): String {
            return "Create a test prompt about $topic"
        }
    }
    
    // Invalid server class for testing validation
    class InvalidServer {
        fun someMethod(): String = "not an MCP method"
    }
}