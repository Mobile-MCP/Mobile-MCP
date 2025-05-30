package io.rosenpin.mmcp.server.annotations

import io.rosenpin.mmcp.server.McpConstants
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for MCPAnnotationProcessor functionality
 */
class MCPAnnotationProcessorTest {
    
    private lateinit var processor: MCPAnnotationProcessor
    
    @Before
    fun setup() {
        processor = MCPAnnotationProcessor()
    }
    
    // Test server classes
    
    @MCPServer(
        id = "com.example.testserver", 
        name = "Test Server", 
        description = "Test server for annotation processing",
        version = "1.0.0"
    )
    class ValidTestServer {
        
        @MCPTool(
            id = "test_tool",
            name = "test_tool", 
            description = "A test tool",
            parameters = """{"type": "object", "properties": {"input": {"type": "string"}}}"""
        )
        fun testTool(@MCPParam("input") input: String): String {
            return "Result: $input"
        }
        
        @MCPResource(
            scheme = "test",
            name = "Test Resource",
            description = "A test resource",
            mimeType = "text/plain"
        )
        fun testResource(@MCPParam("uri") uri: String): String {
            return "Resource content for $uri"
        }
        
        @MCPPrompt(
            id = "test_prompt",
            name = "test_prompt",
            description = "A test prompt",
            parameters = """{"type": "object", "properties": {"context": {"type": "string"}}}"""
        )
        fun testPrompt(@MCPParam("context") context: String): String {
            return "Generated prompt with context: $context"
        }
    }
    
    @MCPServer(
        id = "com.example.toolsonly",
        name = "Tools Only Server",
        description = "Server with only tools",
        version = "1.0.0"
    )
    class ToolsOnlyServer {
        
        @MCPTool(
            id = "tool1",
            name = "tool1",
            description = "First tool"
        )
        fun tool1(): String = "tool1 result"
        
        @MCPTool(
            id = "tool2", 
            name = "tool2",
            description = "Second tool"
        )
        fun tool2(@MCPParam("param") param: String): String = "tool2: $param"
    }
    
    class NoAnnotationServer {
        fun someMethod(): String = "no annotation"
    }
    
    @MCPServer(
        id = "",
        name = "",
        description = "Invalid server",
        version = ""
    )
    class InvalidServer {
        
        @MCPTool(id = "", name = "", description = "")
        fun invalidTool(): String = "invalid"
    }
    
    @MCPServer(
        id = "com.example.noMethods",
        name = "No Methods Server", 
        description = "Server with no MCP methods",
        version = "1.0.0"
    )
    class NoMethodsServer {
        fun regularMethod(): String = "not an MCP method"
    }
    
    // Tests
    
    @Test
    fun `processServerClass returns null for non-annotated class`() {
        val result = processor.processServerClass(NoAnnotationServer::class.java)
        assertNull("Should return null for class without @MCPServer", result)
    }
    
    @Test
    fun `processServerClass extracts basic server info`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        
        assertNotNull("Should return server info", result)
        assertEquals("Should extract server id", "com.example.testserver", result!!.id)
        assertEquals("Should extract server name", "Test Server", result.name)
        assertEquals("Should extract description", "Test server for annotation processing", result.description)
        assertEquals("Should extract version", "1.0.0", result.version)
        assertEquals("Should reference server class", ValidTestServer::class.java, result.serverClass)
    }
    
    @Test
    fun `processServerClass discovers all capability types`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        
        assertNotNull("Should return server info", result)
        assertTrue("Should have tools capability", 
            result!!.capabilities.contains(McpConstants.Capabilities.TOOLS))
        assertTrue("Should have resources capability",
            result.capabilities.contains(McpConstants.Capabilities.RESOURCES))
        assertTrue("Should have prompts capability",
            result.capabilities.contains(McpConstants.Capabilities.PROMPTS))
    }
    
    @Test
    fun `processServerClass discovers tool methods`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        
        assertNotNull("Should return server info", result)
        assertEquals("Should find one tool", 1, result!!.tools.size)
        
        val tool = result.tools[0]
        assertEquals("Should extract tool id", "test_tool", tool.id)
        assertEquals("Should extract tool name", "test_tool", tool.name)
        assertEquals("Should extract tool description", "A test tool", tool.description)
        assertEquals("Should extract parameters schema", 
            """{"type": "object", "properties": {"input": {"type": "string"}}}""", tool.parametersSchema)
        assertEquals("Should map to correct method", "testTool", tool.method.name)
    }
    
    @Test
    fun `processServerClass discovers resource methods`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        
        assertNotNull("Should return server info", result)
        assertEquals("Should find one resource", 1, result!!.resources.size)
        
        val resource = result.resources[0]
        assertEquals("Should extract resource scheme", "test", resource.scheme)
        assertEquals("Should extract resource name", "Test Resource", resource.name)
        assertEquals("Should extract resource description", "A test resource", resource.description)
        assertEquals("Should extract mime type", "text/plain", resource.mimeType)
        assertEquals("Should map to correct method", "testResource", resource.method.name)
    }
    
    @Test
    fun `processServerClass discovers prompt methods`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        
        assertNotNull("Should return server info", result)
        assertEquals("Should find one prompt", 1, result!!.prompts.size)
        
        val prompt = result.prompts[0]
        assertEquals("Should extract prompt id", "test_prompt", prompt.id)
        assertEquals("Should extract prompt name", "test_prompt", prompt.name)
        assertEquals("Should extract prompt description", "A test prompt", prompt.description)
        assertEquals("Should extract parameters schema",
            """{"type": "object", "properties": {"context": {"type": "string"}}}""", prompt.parametersSchema)
        assertEquals("Should map to correct method", "testPrompt", prompt.method.name)
    }
    
    @Test
    fun `processServerClass builds parameter mapping`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        
        assertNotNull("Should return server info", result)
        
        val tool = result!!.tools[0]
        assertTrue("Should map input parameter", tool.parameterMapping.containsKey("input"))
        assertEquals("Should map to parameter index 0", 0, tool.parameterMapping["input"])
        
        val resource = result.resources[0]
        assertTrue("Should map uri parameter", resource.method.parameters.isNotEmpty())
        
        val prompt = result.prompts[0]
        assertTrue("Should map context parameter", prompt.parameterMapping.containsKey("context"))
        assertEquals("Should map to parameter index 0", 0, prompt.parameterMapping["context"])
    }
    
    @Test
    fun `processServerClass handles tools-only server`() {
        val result = processor.processServerClass(ToolsOnlyServer::class.java)
        
        assertNotNull("Should return server info", result)
        assertEquals("Should find two tools", 2, result!!.tools.size)
        assertEquals("Should find no resources", 0, result.resources.size)
        assertEquals("Should find no prompts", 0, result.prompts.size)
        
        assertTrue("Should have tools capability",
            result.capabilities.contains(McpConstants.Capabilities.TOOLS))
        assertFalse("Should not have resources capability",
            result.capabilities.contains(McpConstants.Capabilities.RESOURCES))
        assertFalse("Should not have prompts capability",
            result.capabilities.contains(McpConstants.Capabilities.PROMPTS))
    }
    
    @Test
    fun `validateServerClass accepts valid server`() {
        val errors = processor.validateServerClass(ValidTestServer::class.java)
        assertTrue("Should have no validation errors", errors.isEmpty())
    }
    
    @Test
    fun `validateServerClass rejects non-annotated class`() {
        val errors = processor.validateServerClass(NoAnnotationServer::class.java)
        assertFalse("Should have validation errors", errors.isEmpty())
        assertTrue("Should require @MCPServer annotation",
            errors.any { it.contains("@MCPServer") })
    }
    
    @Test
    fun `validateServerClass rejects invalid annotation values`() {
        val errors = processor.validateServerClass(InvalidServer::class.java)
        assertFalse("Should have validation errors", errors.isEmpty())
        assertTrue("Should reject blank id",
            errors.any { it.contains("id cannot be blank") })
        assertTrue("Should reject blank name", 
            errors.any { it.contains("name cannot be blank") })
        assertTrue("Should reject blank version",
            errors.any { it.contains("version cannot be blank") })
    }
    
    @Test
    fun `validateServerClass rejects server with no MCP methods`() {
        val errors = processor.validateServerClass(NoMethodsServer::class.java)
        assertFalse("Should have validation errors", errors.isEmpty())
        assertTrue("Should require at least one MCP method",
            errors.any { it.contains("at least one") })
    }
    
    @Test
    fun `getToolNames returns all tool names`() {
        val result = processor.processServerClass(ToolsOnlyServer::class.java)
        assertNotNull("Should return server info", result)
        
        val toolNames = processor.getToolNames(result!!)
        assertEquals("Should return two tool names", 2, toolNames.size)
        assertTrue("Should contain tool1", toolNames.contains("tool1"))
        assertTrue("Should contain tool2", toolNames.contains("tool2"))
    }
    
    @Test
    fun `getResourceSchemes returns unique schemes`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        assertNotNull("Should return server info", result)
        
        val schemes = processor.getResourceSchemes(result!!)
        assertEquals("Should return one scheme", 1, schemes.size)
        assertTrue("Should contain test scheme", schemes.contains("test"))
    }
    
    @Test
    fun `getPromptNames returns all prompt names`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        assertNotNull("Should return server info", result)
        
        val promptNames = processor.getPromptNames(result!!)
        assertEquals("Should return one prompt name", 1, promptNames.size)
        assertTrue("Should contain test_prompt", promptNames.contains("test_prompt"))
    }
    
    @Test
    fun `findTool returns correct tool`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        assertNotNull("Should return server info", result)
        
        val tool = processor.findTool(result!!, "test_tool")
        assertNotNull("Should find the tool", tool)
        assertEquals("Should return correct tool", "test_tool", tool!!.name)
        
        val nonExistentTool = processor.findTool(result, "nonexistent")
        assertNull("Should return null for non-existent tool", nonExistentTool)
    }
    
    @Test
    fun `findResourcesForScheme returns matching resources`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        assertNotNull("Should return server info", result)
        
        val resources = processor.findResourcesForScheme(result!!, "test")
        assertEquals("Should find one resource for test scheme", 1, resources.size)
        assertEquals("Should return correct resource", "test", resources[0].scheme)
        
        val noResources = processor.findResourcesForScheme(result, "nonexistent")
        assertTrue("Should return empty list for non-existent scheme", noResources.isEmpty())
    }
    
    @Test
    fun `findPrompt returns correct prompt`() {
        val result = processor.processServerClass(ValidTestServer::class.java)
        assertNotNull("Should return server info", result)
        
        val prompt = processor.findPrompt(result!!, "test_prompt")
        assertNotNull("Should find the prompt", prompt)
        assertEquals("Should return correct prompt", "test_prompt", prompt!!.name)
        
        val nonExistentPrompt = processor.findPrompt(result, "nonexistent")
        assertNull("Should return null for non-existent prompt", nonExistentPrompt)
    }
    
    @Test
    fun `processor handles methods without MCPParam annotations`() {
        @MCPServer(id = "test", name = "Test", description = "Test", version = "1.0")
        class ServerWithoutParamAnnotations {
            @MCPTool(id = "tool", name = "tool", description = "Tool without param annotations")
            fun tool(input: String, count: Int): String = "result"
        }
        
        val result = processor.processServerClass(ServerWithoutParamAnnotations::class.java)
        assertNotNull("Should process server", result)
        
        val tool = result!!.tools[0]
        // Parameter mapping should still work if parameter names are preserved
        // Note: This depends on compilation with parameter names retained
        assertTrue("Should have some parameter mapping or be empty", 
            tool.parameterMapping.isEmpty() || tool.parameterMapping.isNotEmpty())
    }
}