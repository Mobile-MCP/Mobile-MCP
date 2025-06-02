package io.rosenpin.mmcp.server

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.rosenpin.mmcp.server.annotations.*
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Generates comprehensive documentation for MCP servers from their annotations.
 * 
 * This class analyzes MCP server classes and generates human-readable documentation
 * in Markdown format, including API specifications, usage examples, and parameter
 * details extracted from JSON schemas.
 * 
 * Usage:
 * ```kotlin
 * val generator = MCPDocumentationGenerator()
 * val documentation = generator.generateDocumentation(MyMCPService::class.java)
 * println(documentation)
 * ```
 */
class MCPDocumentationGenerator {
    
    companion object {
        private const val TAG = "MCPDocumentationGenerator"
        private val gson = Gson()
    }
    
    /**
     * Generate complete documentation for an MCP server class.
     * 
     * @param serverClass The MCP server class to document
     * @param includeExamples Whether to include usage examples (default: true)
     * @param includeInternalMethods Whether to include non-public methods (default: false)
     * @return Complete documentation in Markdown format
     */
    fun generateDocumentation(
        serverClass: Class<*>,
        includeExamples: Boolean = true,
        includeInternalMethods: Boolean = false
    ): String {
        val serverAnnotation = serverClass.getAnnotation(MCPServer::class.java)
            ?: return "⚠️ **Error**: Class `${serverClass.simpleName}` is not annotated with `@MCPServer`"
        
        val sb = StringBuilder()
        
        // Header
        sb.appendLine("# ${serverAnnotation.name}")
        sb.appendLine()
        sb.appendLine("**Server ID**: `${serverAnnotation.id}`  ")
        sb.appendLine("**Version**: `${serverAnnotation.version}`  ")
        sb.appendLine("**Package**: `${serverClass.packageName}`  ")
        sb.appendLine("**Class**: `${serverClass.simpleName}`")
        sb.appendLine()
        
        // Description
        sb.appendLine("## Description")
        sb.appendLine()
        sb.appendLine(serverAnnotation.description)
        sb.appendLine()
        
        // Capabilities
        val capabilities = determineCapabilities(serverClass)
        if (capabilities.isNotEmpty()) {
            sb.appendLine("## Capabilities")
            sb.appendLine()
            capabilities.forEach { capability ->
                sb.appendLine("- ✅ **${capability.replaceFirstChar { it.uppercaseChar() }}**")
            }
            sb.appendLine()
        }
        
        // Tools
        val tools = getAnnotatedMethods<MCPTool>(serverClass, includeInternalMethods)
        if (tools.isNotEmpty()) {
            sb.appendLine("## 🔧 Tools")
            sb.appendLine()
            sb.appendLine("This server provides the following tools:")
            sb.appendLine()
            
            tools.forEach { (method, annotation) ->
                sb.append(generateToolDocumentation(method, annotation, includeExamples))
            }
        }
        
        // Resources
        val resources = getAnnotatedMethods<MCPResource>(serverClass, includeInternalMethods)
        if (resources.isNotEmpty()) {
            sb.appendLine("## 📁 Resources")
            sb.appendLine()
            sb.appendLine("This server provides the following resources:")
            sb.appendLine()
            
            resources.forEach { (method, annotation) ->
                sb.append(generateResourceDocumentation(method, annotation, includeExamples))
            }
        }
        
        // Prompts
        val prompts = getAnnotatedMethods<MCPPrompt>(serverClass, includeInternalMethods)
        if (prompts.isNotEmpty()) {
            sb.appendLine("## 💬 Prompts")
            sb.appendLine()
            sb.appendLine("This server provides the following prompts:")
            sb.appendLine()
            
            prompts.forEach { (method, annotation) ->
                sb.append(generatePromptDocumentation(method, annotation, includeExamples))
            }
        }
        
        // Installation
        if (includeExamples) {
            sb.appendLine("## 📦 Installation & Setup")
            sb.appendLine()
            sb.append(generateInstallationInstructions(serverClass, serverAnnotation))
        }
        
        // Usage Examples
        if (includeExamples && (tools.isNotEmpty() || resources.isNotEmpty() || prompts.isNotEmpty())) {
            sb.appendLine("## 📖 Usage Examples")
            sb.appendLine()
            sb.append(generateUsageExamples(tools, resources, prompts))
        }
        
        return sb.toString()
    }
    
    /**
     * Generate documentation for a specific tool method.
     */
    private fun generateToolDocumentation(method: Method, annotation: MCPTool, includeExamples: Boolean): String {
        val sb = StringBuilder()
        
        sb.appendLine("### ${annotation.name}")
        sb.appendLine()
        sb.appendLine("**ID**: `${annotation.id}`  ")
        sb.appendLine("**Method**: `${method.name}`")
        sb.appendLine()
        sb.appendLine(annotation.description)
        sb.appendLine()
        
        // Parameters
        if (annotation.parameters.isNotBlank() && annotation.parameters != "{}") {
            sb.appendLine("#### Parameters")
            sb.appendLine()
            sb.append(generateParametersDocumentation(annotation.parameters))
            sb.appendLine()
        } else {
            sb.appendLine("*No parameters required.*")
            sb.appendLine()
        }
        
        // Method signature
        sb.appendLine("#### Method Signature")
        sb.appendLine()
        sb.appendLine("```kotlin")
        sb.appendLine(generateMethodSignature(method))
        sb.appendLine("```")
        sb.appendLine()
        
        if (includeExamples) {
            sb.appendLine("#### Example Usage")
            sb.appendLine()
            sb.append(generateToolExample(annotation))
        }
        
        sb.appendLine("---")
        sb.appendLine()
        
        return sb.toString()
    }
    
    /**
     * Generate documentation for a specific resource method.
     */
    private fun generateResourceDocumentation(method: Method, annotation: MCPResource, includeExamples: Boolean): String {
        val sb = StringBuilder()
        
        sb.appendLine("### ${annotation.name}")
        sb.appendLine()
        sb.appendLine("**URI Scheme**: `${annotation.scheme}://`  ")
        sb.appendLine("**MIME Type**: `${annotation.mimeType}`  ")
        sb.appendLine("**Method**: `${method.name}`")
        sb.appendLine()
        sb.appendLine(annotation.description)
        sb.appendLine()
        
        if (includeExamples) {
            sb.appendLine("#### Example Usage")
            sb.appendLine()
            sb.append(generateResourceExample(annotation))
        }
        
        sb.appendLine("---")
        sb.appendLine()
        
        return sb.toString()
    }
    
    /**
     * Generate documentation for a specific prompt method.
     */
    private fun generatePromptDocumentation(method: Method, annotation: MCPPrompt, includeExamples: Boolean): String {
        val sb = StringBuilder()
        
        sb.appendLine("### ${annotation.name}")
        sb.appendLine()
        sb.appendLine("**ID**: `${annotation.id}`  ")
        sb.appendLine("**Method**: `${method.name}`")
        sb.appendLine()
        sb.appendLine(annotation.description)
        sb.appendLine()
        
        // Parameters
        if (annotation.parameters.isNotBlank() && annotation.parameters != "{}") {
            sb.appendLine("#### Parameters")
            sb.appendLine()
            sb.append(generateParametersDocumentation(annotation.parameters))
            sb.appendLine()
        } else {
            sb.appendLine("*No parameters required.*")
            sb.appendLine()
        }
        
        if (includeExamples) {
            sb.appendLine("#### Example Usage")
            sb.appendLine()
            sb.append(generatePromptExample(annotation))
        }
        
        sb.appendLine("---")
        sb.appendLine()
        
        return sb.toString()
    }
    
    /**
     * Generate parameter documentation from JSON schema.
     */
    private fun generateParametersDocumentation(parametersSchema: String): String {
        val sb = StringBuilder()
        
        try {
            val schema = gson.fromJson(parametersSchema, Map::class.java) as Map<*, *>
            val properties = schema["properties"] as? Map<*, *>
            val required = schema["required"] as? List<*> ?: emptyList<String>()
            
            if (properties != null) {
                sb.appendLine("| Parameter | Type | Required | Description |")
                sb.appendLine("|-----------|------|----------|-------------|")
                
                properties.forEach { (name, prop) ->
                    prop as Map<*, *>
                    val type = prop["type"] as? String ?: "any"
                    val description = prop["description"] as? String ?: ""
                    val isRequired = required.contains(name)
                    val enumValues = prop["enum"] as? List<*>
                    val defaultValue = prop["default"]
                    
                    val typeDesc = buildString {
                        append("`$type`")
                        if (enumValues != null) {
                            append("<br/>Options: ${enumValues.joinToString(", ") { "`$it`" }}")
                        }
                        if (defaultValue != null) {
                            append("<br/>Default: `$defaultValue`")
                        }
                    }
                    
                    val requiredMark = if (isRequired) "✅" else "❌"
                    
                    sb.appendLine("| `$name` | $typeDesc | $requiredMark | $description |")
                }
            }
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Failed to parse parameters schema", e)
            sb.appendLine("*Parameters schema format error*")
            sb.appendLine()
            sb.appendLine("```json")
            sb.appendLine(parametersSchema)
            sb.appendLine("```")
        }
        
        return sb.toString()
    }
    
    /**
     * Generate installation instructions.
     */
    private fun generateInstallationInstructions(serverClass: Class<*>, serverAnnotation: MCPServer): String {
        val sb = StringBuilder()
        
        sb.appendLine("### 1. Add Dependency")
        sb.appendLine()
        sb.appendLine("```kotlin")
        sb.appendLine("dependencies {")
        sb.appendLine("    implementation \"io.rosenpin.mmcp:mmcp-server-android:1.0.0\"")
        sb.appendLine("}")
        sb.appendLine("```")
        sb.appendLine()
        
        sb.appendLine("### 2. AndroidManifest.xml")
        sb.appendLine()
        sb.appendLine("Add the following to your `AndroidManifest.xml`:")
        sb.appendLine()
        sb.appendLine("```xml")
        sb.appendLine(MCPServerHelper.generateManifestXml(serverClass.name))
        sb.appendLine("```")
        sb.appendLine()
        
        sb.appendLine("### 3. Permissions")
        sb.appendLine()
        sb.appendLine("Add any required permissions to your `AndroidManifest.xml`:")
        sb.appendLine()
        sb.appendLine("```xml")
        sb.appendLine("<!-- Example permissions - adjust based on your server's needs -->")
        sb.appendLine("<uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" />")
        sb.appendLine("<uses-permission android:name=\"android.permission.READ_CONTACTS\" />")
        sb.appendLine("```")
        sb.appendLine()
        
        return sb.toString()
    }
    
    /**
     * Generate usage examples.
     */
    private fun generateUsageExamples(
        tools: List<Pair<Method, MCPTool>>,
        resources: List<Pair<Method, MCPResource>>,
        prompts: List<Pair<Method, MCPPrompt>>
    ): String {
        val sb = StringBuilder()
        
        sb.appendLine("### Kotlin Client Code")
        sb.appendLine()
        sb.appendLine("```kotlin")
        sb.appendLine("// Initialize MCP client")
        sb.appendLine("val mcpClient = McpClient(context)")
        sb.appendLine("mcpClient.startDiscovery()")
        sb.appendLine()
        sb.appendLine("// Connect to this server")
        sb.appendLine("val connection = mcpClient.connectToServer(\"${tools.firstOrNull()?.second?.let { getPackageFromId(it.id) } ?: "com.example.app"}\")")
        sb.appendLine()
        
        if (tools.isNotEmpty()) {
            sb.appendLine("// Execute tools")
            tools.take(2).forEach { (_, annotation) ->
                sb.appendLine("val result = mcpClient.executeTool(")
                sb.appendLine("    packageName = \"${getPackageFromId(annotation.id)}\",")
                sb.appendLine("    toolName = \"${annotation.name}\",")
                sb.appendLine("    parameters = mapOf(")
                sb.appendLine("        // Add parameters based on tool schema")
                sb.appendLine("    )")
                sb.appendLine(")")
                sb.appendLine()
            }
        }
        
        if (resources.isNotEmpty()) {
            sb.appendLine("// Access resources")
            resources.take(2).forEach { (_, annotation) ->
                sb.appendLine("val resourceData = mcpClient.readResource(")
                sb.appendLine("    packageName = \"${getPackageFromId(annotation.scheme)}\",")
                sb.appendLine("    resourceUri = \"${annotation.scheme}://example/path\"")
                sb.appendLine(")")
                sb.appendLine()
            }
        }
        
        if (prompts.isNotEmpty()) {
            sb.appendLine("// Get prompts")
            prompts.take(2).forEach { (_, annotation) ->
                sb.appendLine("val prompt = mcpClient.getPrompt(")
                sb.appendLine("    packageName = \"${getPackageFromId(annotation.id)}\",")
                sb.appendLine("    promptName = \"${annotation.name}\",")
                sb.appendLine("    parameters = mapOf(")
                sb.appendLine("        // Add parameters based on prompt schema")
                sb.appendLine("    )")
                sb.appendLine(")")
                sb.appendLine()
            }
        }
        
        sb.appendLine("```")
        sb.appendLine()
        
        return sb.toString()
    }
    
    /**
     * Generate example usage for a tool.
     */
    private fun generateToolExample(annotation: MCPTool): String {
        return """
            ```http
            POST /tools/call
            Content-Type: application/json
            
            {
                "name": "${annotation.name}",
                "arguments": {
                    // Parameters based on the schema above
                }
            }
            ```
            
        """.trimIndent() + "\n"
    }
    
    /**
     * Generate example usage for a resource.
     */
    private fun generateResourceExample(annotation: MCPResource): String {
        return """
            ```http
            GET /resources/read
            Content-Type: application/json
            
            {
                "uri": "${annotation.scheme}://example/path"
            }
            ```
            
        """.trimIndent() + "\n"
    }
    
    /**
     * Generate example usage for a prompt.
     */
    private fun generatePromptExample(annotation: MCPPrompt): String {
        return """
            ```http
            POST /prompts/get
            Content-Type: application/json
            
            {
                "name": "${annotation.name}",
                "arguments": {
                    // Parameters based on the schema above
                }
            }
            ```
            
        """.trimIndent() + "\n"
    }
    
    /**
     * Generate method signature string.
     */
    private fun generateMethodSignature(method: Method): String {
        val params = method.parameters.joinToString(", ") { param ->
            val annotation = param.getAnnotation(MCPParam::class.java)
            val paramName = annotation?.name ?: param.name
            "$paramName: ${param.type.simpleName}"
        }
        
        return "${method.returnType.simpleName} ${method.name}($params)"
    }
    
    /**
     * Get all methods annotated with a specific annotation.
     */
    private inline fun <reified T : Annotation> getAnnotatedMethods(
        clazz: Class<*>,
        includeInternalMethods: Boolean
    ): List<Pair<Method, T>> {
        return clazz.methods
            .filter { method ->
                method.isAnnotationPresent(T::class.java) &&
                        (includeInternalMethods || java.lang.reflect.Modifier.isPublic(method.modifiers))
            }
            .map { method -> method to method.getAnnotation(T::class.java) }
    }
    
    /**
     * Determine server capabilities from annotated methods.
     */
    private fun determineCapabilities(serverClass: Class<*>): List<String> {
        val capabilities = mutableListOf<String>()
        
        if (serverClass.methods.any { it.isAnnotationPresent(MCPTool::class.java) }) {
            capabilities.add("tools")
        }
        
        if (serverClass.methods.any { it.isAnnotationPresent(MCPResource::class.java) }) {
            capabilities.add("resources")
        }
        
        if (serverClass.methods.any { it.isAnnotationPresent(MCPPrompt::class.java) }) {
            capabilities.add("prompts")
        }
        
        return capabilities
    }
    
    /**
     * Extract package name from MCP ID.
     */
    private fun getPackageFromId(id: String): String {
        return id.substringBeforeLast(".", "com.example.app")
    }
}