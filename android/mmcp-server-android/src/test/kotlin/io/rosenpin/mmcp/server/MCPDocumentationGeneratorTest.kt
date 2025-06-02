package io.rosenpin.mmcp.server

import io.rosenpin.mmcp.server.annotations.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Test suite for MCPDocumentationGenerator
 */
class MCPDocumentationGeneratorTest {
    
    @MCPServer(
        id = "com.example.test",
        name = "Test Server",
        description = "A test server for documentation generation",
        version = "1.0.0"
    )
    class TestMCPServer {
        
        @MCPTool(
            id = "test_tool",
            name = "Test Tool",
            description = "A test tool for documentation",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name parameter"
                    },
                    "count": {
                        "type": "number",
                        "description": "Count parameter"
                    }
                },
                "required": ["name"]
            }
            """
        )
        fun testTool(@MCPParam("name") name: String, @MCPParam("count") count: Int = 1): String {
            return "Test result: $name x $count"
        }
        
        @MCPResource(
            scheme = "test",
            name = "Test Resource",
            description = "A test resource",
            mimeType = "text/plain"
        )
        fun testResource(uri: String): String {
            return "Test resource content for: $uri"
        }
        
        @MCPPrompt(
            id = "test_prompt",
            name = "Test Prompt",
            description = "A test prompt generator",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "topic": {
                        "type": "string",
                        "description": "Topic to generate prompt about"
                    }
                }
            }
            """
        )
        fun testPrompt(@MCPParam("topic") topic: String = "general"): String {
            return "Generated prompt about: $topic"
        }
    }
    
    @Test
    fun testGenerateDocumentation() {
        val generator = MCPDocumentationGenerator()
        val docs = generator.generateDocumentation(TestMCPServer::class.java)
        
        // Check header information
        assertTrue("Should contain server name", docs.contains("# Test Server"))
        assertTrue("Should contain server ID", docs.contains("com.example.test"))
        assertTrue("Should contain version", docs.contains("1.0.0"))
        assertTrue("Should contain description", docs.contains("A test server for documentation generation"))
        
        // Check capabilities
        assertTrue("Should list tools capability", docs.contains("Tools"))
        assertTrue("Should list resources capability", docs.contains("Resources"))
        assertTrue("Should list prompts capability", docs.contains("Prompts"))
        
        // Check tools section
        assertTrue("Should contain tools section", docs.contains("🔧 Tools"))
        assertTrue("Should contain test tool", docs.contains("Test Tool"))
        assertTrue("Should contain tool description", docs.contains("A test tool for documentation"))
        assertTrue("Should contain parameters table", docs.contains("| Parameter | Type |"))
        assertTrue("Should list name parameter", docs.contains("| `name` |"))
        assertTrue("Should list count parameter", docs.contains("| `count` |"))
        
        // Check resources section
        assertTrue("Should contain resources section", docs.contains("📁 Resources"))
        assertTrue("Should contain test resource", docs.contains("Test Resource"))
        assertTrue("Should contain URI scheme", docs.contains("test://"))
        
        // Check prompts section
        assertTrue("Should contain prompts section", docs.contains("💬 Prompts"))
        assertTrue("Should contain test prompt", docs.contains("Test Prompt"))
        
        // Check installation section
        assertTrue("Should contain installation section", docs.contains("📦 Installation & Setup"))
        assertTrue("Should contain manifest XML", docs.contains("AndroidManifest.xml"))
        
        // Check usage examples
        assertTrue("Should contain usage examples", docs.contains("📖 Usage Examples"))
        assertTrue("Should contain Kotlin client code", docs.contains("McpClient"))
    }
    
    @Test
    fun testGenerateDocumentationForNonMCPClass() {
        val generator = MCPDocumentationGenerator()
        
        class NonMCPClass
        
        val docs = generator.generateDocumentation(NonMCPClass::class.java)
        
        assertTrue("Should contain error message", docs.contains("Error"))
        assertTrue("Should mention missing annotation", docs.contains("@MCPServer"))
    }
    
    @Test
    fun testDocumentationWithoutExamples() {
        val generator = MCPDocumentationGenerator()
        val docs = generator.generateDocumentation(TestMCPServer::class.java, includeExamples = false)
        
        assertTrue("Should contain server info", docs.contains("Test Server"))
        assertFalse("Should not contain installation section", docs.contains("📦 Installation & Setup"))
        assertFalse("Should not contain usage examples", docs.contains("📖 Usage Examples"))
    }
}