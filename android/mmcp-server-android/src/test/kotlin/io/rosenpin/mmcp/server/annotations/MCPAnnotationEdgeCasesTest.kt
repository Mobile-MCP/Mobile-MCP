package io.rosenpin.mmcp.server.annotations

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Edge case tests for MCPAnnotationProcessor
 */
class MCPAnnotationEdgeCasesTest {
    
    private lateinit var processor: MCPAnnotationProcessor
    
    @Before
    fun setup() {
        processor = MCPAnnotationProcessor()
    }
    
    // Edge case test classes
    
    @MCPServer(
        id = "com.example.complex",
        name = "Complex Server",
        description = "Server with complex scenarios",
        version = "1.0.0"
    )
    class ComplexServer {
        
        @MCPTool(
            id = "complex_tool",
            name = "complex_tool",
            description = "Tool with complex parameters",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "requiredParam": {"type": "string"},
                    "optionalParam": {"type": "integer", "default": 42},
                    "nestedObject": {
                        "type": "object",
                        "properties": {
                            "nestedField": {"type": "string"}
                        }
                    }
                },
                "required": ["requiredParam"]
            }
            """
        )
        fun complexTool(
            @MCPParam("requiredParam") required: String,
            @MCPParam("optionalParam") optional: Int?,
            @MCPParam("nestedObject") nested: Map<String, Any>?
        ): String {
            return "Complex result"
        }
        
        @MCPTool(
            id = "no_params_tool",
            name = "no_params_tool", 
            description = "Tool with no parameters"
        )
        fun noParamsTool(): String {
            return "No params result"
        }
        
        @MCPTool(
            id = "varargs_tool",
            name = "varargs_tool",
            description = "Tool with varargs"
        )
        fun varargsTool(@MCPParam("items") vararg items: String): String {
            return "Varargs result: ${items.joinToString()}"
        }
        
        @MCPResource(
            scheme = "https",
            name = "HTTPS Resource",
            description = "Resource with HTTPS scheme"
        )
        fun httpsResource(@MCPParam("url") url: String): ByteArray {
            return "HTTPS content".toByteArray()
        }
        
        @MCPResource(
            scheme = "file",
            name = "File Resource", 
            description = "File resource with custom mime type",
            mimeType = "application/json"
        )
        fun fileResource(@MCPParam("path") path: String): ByteArray {
            return """{"file": "$path"}""".toByteArray()
        }
        
        // Multiple resources with same scheme
        @MCPResource(
            scheme = "file",
            name = "Another File Resource",
            description = "Another file resource handler"
        )
        fun anotherFileResource(@MCPParam("uri") uri: String): ByteArray {
            return "Another file content".toByteArray()
        }
        
        @MCPPrompt(
            id = "conditional_prompt",
            name = "conditional_prompt",
            description = "Prompt with conditional logic",
            parameters = """
            {
                "type": "object", 
                "properties": {
                    "condition": {"type": "boolean"},
                    "trueValue": {"type": "string"},
                    "falseValue": {"type": "string"}
                }
            }
            """
        )
        fun conditionalPrompt(
            @MCPParam("condition") condition: Boolean,
            @MCPParam("trueValue") trueValue: String?,
            @MCPParam("falseValue") falseValue: String?
        ): String {
            return if (condition) trueValue ?: "default true" else falseValue ?: "default false"
        }
    }
    
    @MCPServer(
        id = "com.example.inheritance",
        name = "Inheritance Server",
        description = "Server that uses inheritance",
        version = "1.0.0"
    )
    abstract class BaseServer {
        
        @MCPTool(
            id = "base_tool",
            name = "base_tool",
            description = "Tool from base class"
        )
        fun baseTool(@MCPParam("input") input: String): String {
            return "Base: $input"
        }
        
        abstract fun abstractMethod(): String
    }
    
    @MCPServer(
        id = "com.example.concrete",
        name = "Concrete Server",
        description = "Concrete implementation server",
        version = "1.0.0"
    )
    class ConcreteServer : BaseServer() {
        
        @MCPTool(
            id = "concrete_tool",
            name = "concrete_tool",
            description = "Tool from concrete class"
        )
        fun concreteTool(@MCPParam("value") value: Int): String {
            return "Concrete: $value"
        }
        
        override fun abstractMethod(): String {
            return "Implemented"
        }
    }
    
    @MCPServer(
        id = "com.example.unicode",
        name = "Unicode Server 🚀",
        description = "Server with unicode characters in metadata 测试",
        version = "1.0.0"
    )
    class UnicodeServer {
        
        @MCPTool(
            id = "unicode_tool",
            name = "unicode_tool_测试",
            description = "Tool with unicode description 🎯"
        )
        fun unicodeTool(@MCPParam("输入") input: String): String {
            return "Unicode result: $input"
        }
    }
    
    @MCPServer(
        id = "com.example.malformed", 
        name = "Malformed Server",
        description = "Server with malformed JSON",
        version = "1.0.0"
    )
    class MalformedJsonServer {
        
        @MCPTool(
            id = "malformed_tool",
            name = "malformed_tool",
            description = "Tool with malformed JSON schema",
            parameters = """{"type": "object", "properties": {"param": {"type": "string"}""" // Missing closing braces
        )
        fun malformedTool(@MCPParam("param") param: String): String {
            return "Result"
        }
    }
    
    // Tests
    
    @Test
    fun `processor handles complex parameter schemas`() {
        val result = processor.processServerClass(ComplexServer::class.java)
        
        assertNotNull("Should process complex server", result)
        
        val complexTool = processor.findTool(result!!, "complex_tool")
        assertNotNull("Should find complex tool", complexTool)
        
        assertTrue("Should contain complex JSON schema", 
            complexTool!!.parametersSchema.contains("nestedObject"))
        assertTrue("Should contain required array",
            complexTool.parametersSchema.contains("required"))
        
        assertEquals("Should map all parameters", 3, complexTool.parameterMapping.size)
        assertTrue("Should map requiredParam", complexTool.parameterMapping.containsKey("requiredParam"))
        assertTrue("Should map optionalParam", complexTool.parameterMapping.containsKey("optionalParam"))
        assertTrue("Should map nestedObject", complexTool.parameterMapping.containsKey("nestedObject"))
    }
    
    @Test
    fun `processor handles tools with no parameters`() {
        val result = processor.processServerClass(ComplexServer::class.java)
        
        assertNotNull("Should process server", result)
        
        val noParamsTool = processor.findTool(result!!, "no_params_tool")
        assertNotNull("Should find no params tool", noParamsTool)
        
        assertEquals("Should have empty parameter schema", "{}", noParamsTool!!.parametersSchema)
        assertTrue("Should have empty parameter mapping", noParamsTool.parameterMapping.isEmpty())
    }
    
    @Test
    fun `processor handles multiple resources with same scheme`() {
        val result = processor.processServerClass(ComplexServer::class.java)
        
        assertNotNull("Should process server", result)
        
        val fileResources = processor.findResourcesForScheme(result!!, "file")
        assertEquals("Should find two file resources", 2, fileResources.size)
        
        val resourceNames = fileResources.map { it.name }
        assertTrue("Should contain first file resource", resourceNames.contains("File Resource"))
        assertTrue("Should contain second file resource", resourceNames.contains("Another File Resource"))
    }
    
    @Test
    fun `processor discovers inherited methods`() {
        val result = processor.processServerClass(ConcreteServer::class.java)
        
        assertNotNull("Should process concrete server", result)
        assertEquals("Should find tools from both base and concrete class", 2, result!!.tools.size)
        
        val toolNames = processor.getToolNames(result)
        assertTrue("Should contain base tool", toolNames.contains("base_tool"))
        assertTrue("Should contain concrete tool", toolNames.contains("concrete_tool"))
    }
    
    @Test
    fun `processor handles unicode characters`() {
        val result = processor.processServerClass(UnicodeServer::class.java)
        
        assertNotNull("Should process unicode server", result)
        assertEquals("Should preserve unicode in server name", "Unicode Server 🚀", result!!.name)
        assertEquals("Should preserve unicode in description", "Server with unicode characters in metadata 测试", result.description)
        
        val unicodeTool = processor.findTool(result, "unicode_tool_测试")
        assertNotNull("Should find unicode tool", unicodeTool)
        assertEquals("Should preserve unicode in tool description", "Tool with unicode description 🎯", unicodeTool!!.description)
        
        assertTrue("Should map unicode parameter name", unicodeTool.parameterMapping.containsKey("输入"))
    }
    
    @Test
    fun `processor detects malformed JSON schemas`() {
        val errors = processor.validateServerClass(MalformedJsonServer::class.java)
        
        assertFalse("Should have validation errors", errors.isEmpty())
        assertTrue("Should detect malformed JSON",
            errors.any { it.contains("JSON") })
    }
    
    @Test
    fun `processor handles different mime types`() {
        val result = processor.processServerClass(ComplexServer::class.java)
        
        assertNotNull("Should process server", result)
        
        val fileResources = processor.findResourcesForScheme(result!!, "file")
        val jsonResource = fileResources.find { it.name == "File Resource" }
        val defaultResource = fileResources.find { it.name == "Another File Resource" }
        
        assertNotNull("Should find JSON resource", jsonResource)
        assertNotNull("Should find default resource", defaultResource)
        
        assertEquals("Should have custom mime type", "application/json", jsonResource!!.mimeType)
        assertEquals("Should have default mime type", "application/octet-stream", defaultResource!!.mimeType)
    }
    
    @Test
    fun `processor handles nullable parameters`() {
        val result = processor.processServerClass(ComplexServer::class.java)
        
        assertNotNull("Should process server", result)
        
        val complexTool = processor.findTool(result!!, "complex_tool")
        assertNotNull("Should find complex tool", complexTool)
        
        // Verify the method has nullable parameters (Java reflection doesn't give us nullability info directly,
        // but the method should still be discoverable)
        assertEquals("Should have correct number of parameters", 3, complexTool!!.method.parameterCount)
    }
    
    @Test
    fun `processor handles varargs parameters`() {
        val result = processor.processServerClass(ComplexServer::class.java)
        
        assertNotNull("Should process server", result)
        
        val varargsTool = processor.findTool(result!!, "varargs_tool")
        assertNotNull("Should find varargs tool", varargsTool)
        
        assertTrue("Should map varargs parameter", varargsTool!!.parameterMapping.containsKey("items"))
        assertTrue("Should handle varargs method", varargsTool.method.isVarArgs)
    }
    
    @Test
    fun `processor validates empty strings vs blank strings`() {
        @MCPServer(id = "   ", name = "\t", description = "", version = "  ")
        class WhitespaceServer {
            @MCPTool(id = " ", name = "", description = "")
            fun tool(): String = "result"
        }
        
        val errors = processor.validateServerClass(WhitespaceServer::class.java)
        
        assertFalse("Should have validation errors for whitespace", errors.isEmpty())
        assertTrue("Should reject whitespace-only id", 
            errors.any { it.contains("id cannot be blank") })
        assertTrue("Should reject whitespace-only name",
            errors.any { it.contains("name cannot be blank") })
    }
    
    @Test
    fun `processor handles very long annotation values`() {
        // Use compile-time string literals
        @MCPServer(
            id = "com.example.verylongidxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            name = "Long Server",
            description = "Very long description with lots of text that goes on and on and on to test how the processor handles long annotation values in server descriptions",
            version = "1.0.0"
        )
        class LongAnnotationServer {
            @MCPTool(id = "tool", name = "tool", description = "Long tool description with lots of text that goes on and on and on to test how the processor handles long annotation values in tool descriptions")
            fun tool(): String = "result"
        }
        
        val result = processor.processServerClass(LongAnnotationServer::class.java)
        
        assertNotNull("Should handle long annotations", result)
        assertTrue("Should preserve long id", result!!.id.length > 50)
        assertTrue("Should preserve long description", result.description.length > 100)
        
        val tool = result.tools[0]
        assertTrue("Should preserve long tool description", tool.description.length > 100)
    }
}