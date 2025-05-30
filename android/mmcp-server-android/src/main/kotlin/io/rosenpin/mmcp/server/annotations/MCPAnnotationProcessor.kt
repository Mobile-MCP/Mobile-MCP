package io.rosenpin.mmcp.server.annotations

import io.rosenpin.mmcp.mmcpcore.protocol.McpMethods
import io.rosenpin.mmcp.server.McpConstants
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Reflection-based processor for discovering MCP capabilities from annotated classes.
 * 
 * This processor scans classes for MCP annotations and builds the metadata needed
 * to expose methods as MCP tools, resources, and prompts through AIDL services.
 */
class MCPAnnotationProcessor {
    
    companion object {
        private const val TAG = "MCPAnnotationProcessor"
    }
    
    /**
     * Process a server class and extract all MCP metadata.
     * 
     * @param serverClass The class to process for MCP annotations
     * @return ServerInfo containing all discovered capabilities, or null if no @MCPServer annotation
     */
    fun processServerClass(serverClass: Class<*>): ServerInfo? {
        val serverAnnotation = serverClass.getAnnotation(MCPServer::class.java)
            ?: return null
        
        val tools = findToolMethods(serverClass)
        val resources = findResourceMethods(serverClass)
        val prompts = findPromptMethods(serverClass)
        
        val capabilities = buildCapabilitiesList(tools, resources, prompts)
        
        return ServerInfo(
            id = serverAnnotation.id,
            name = serverAnnotation.name,
            description = serverAnnotation.description,
            version = serverAnnotation.version,
            capabilities = capabilities,
            tools = tools,
            resources = resources,
            prompts = prompts,
            serverClass = serverClass
        )
    }
    
    /**
     * Find all methods annotated with @MCPTool.
     */
    private fun findToolMethods(serverClass: Class<*>): List<ToolMethodInfo> {
        return serverClass.methods
            .filter { it.isAnnotationPresent(MCPTool::class.java) }
            .map { method ->
                val annotation = method.getAnnotation(MCPTool::class.java)
                val parameterMapping = buildParameterMapping(method)
                
                ToolMethodInfo(
                    id = annotation.id,
                    name = annotation.name,
                    description = annotation.description,
                    parametersSchema = annotation.parameters,
                    method = method,
                    parameterMapping = parameterMapping
                )
            }
    }
    
    /**
     * Find all methods annotated with @MCPResource.
     */
    private fun findResourceMethods(serverClass: Class<*>): List<ResourceMethodInfo> {
        return serverClass.methods
            .filter { it.isAnnotationPresent(MCPResource::class.java) }
            .map { method ->
                val annotation = method.getAnnotation(MCPResource::class.java)
                
                ResourceMethodInfo(
                    scheme = annotation.scheme,
                    name = annotation.name,
                    description = annotation.description,
                    mimeType = annotation.mimeType,
                    method = method
                )
            }
    }
    
    /**
     * Find all methods annotated with @MCPPrompt.
     */
    private fun findPromptMethods(serverClass: Class<*>): List<PromptMethodInfo> {
        return serverClass.methods
            .filter { it.isAnnotationPresent(MCPPrompt::class.java) }
            .map { method ->
                val annotation = method.getAnnotation(MCPPrompt::class.java)
                val parameterMapping = buildParameterMapping(method)
                
                PromptMethodInfo(
                    id = annotation.id,
                    name = annotation.name,
                    description = annotation.description,
                    parametersSchema = annotation.parameters,
                    method = method,
                    parameterMapping = parameterMapping
                )
            }
    }
    
    /**
     * Build parameter mapping from @MCPParam annotations.
     * Maps MCP parameter names to method parameter indices for correct invocation.
     */
    private fun buildParameterMapping(method: Method): Map<String, Int> {
        val mapping = mutableMapOf<String, Int>()
        
        method.parameters.forEachIndexed { index, parameter ->
            val paramAnnotation = parameter.getAnnotation(MCPParam::class.java)
            if (paramAnnotation != null) {
                mapping[paramAnnotation.name] = index
            } else {
                // If no @MCPParam annotation, use parameter name if available
                if (parameter.isNamePresent) {
                    mapping[parameter.name] = index
                }
            }
        }
        
        return mapping
    }
    
    /**
     * Build the capabilities list based on discovered methods.
     */
    private fun buildCapabilitiesList(
        tools: List<ToolMethodInfo>,
        resources: List<ResourceMethodInfo>, 
        prompts: List<PromptMethodInfo>
    ): List<String> {
        val capabilities = mutableListOf<String>()
        
        if (tools.isNotEmpty()) {
            capabilities.add(McpConstants.Capabilities.TOOLS)
        }
        
        if (resources.isNotEmpty()) {
            capabilities.add(McpConstants.Capabilities.RESOURCES)
        }
        
        if (prompts.isNotEmpty()) {
            capabilities.add(McpConstants.Capabilities.PROMPTS)
        }
        
        return capabilities
    }
    
    /**
     * Validate that a server class is properly annotated and configured.
     * 
     * @param serverClass The class to validate
     * @return List of validation errors, empty if valid
     */
    fun validateServerClass(serverClass: Class<*>): List<String> {
        val errors = mutableListOf<String>()
        
        // Check for @MCPServer annotation
        if (!serverClass.isAnnotationPresent(MCPServer::class.java)) {
            errors.add("Class must be annotated with @MCPServer")
            return errors
        }
        
        val serverAnnotation = serverClass.getAnnotation(MCPServer::class.java)
        
        // Validate server annotation fields
        if (serverAnnotation.id.isBlank()) {
            errors.add("@MCPServer id cannot be blank")
        }
        
        if (serverAnnotation.name.isBlank()) {
            errors.add("@MCPServer name cannot be blank")
        }
        
        if (serverAnnotation.version.isBlank()) {
            errors.add("@MCPServer version cannot be blank")
        }
        
        // Check for at least one MCP method
        val hasTools = serverClass.methods.any { it.isAnnotationPresent(MCPTool::class.java) }
        val hasResources = serverClass.methods.any { it.isAnnotationPresent(MCPResource::class.java) }
        val hasPrompts = serverClass.methods.any { it.isAnnotationPresent(MCPPrompt::class.java) }
        
        if (!hasTools && !hasResources && !hasPrompts) {
            errors.add("Server must have at least one @MCPTool, @MCPResource, or @MCPPrompt method")
        }
        
        // Validate tool methods
        serverClass.methods
            .filter { it.isAnnotationPresent(MCPTool::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(MCPTool::class.java)
                if (annotation.id.isBlank()) {
                    errors.add("@MCPTool id cannot be blank on method ${method.name}")
                }
                if (annotation.name.isBlank()) {
                    errors.add("@MCPTool name cannot be blank on method ${method.name}")
                }
                
                // Validate JSON schema if provided
                if (annotation.parameters.isNotBlank() && annotation.parameters != "{}") {
                    if (!isValidBasicJson(annotation.parameters)) {
                        errors.add("@MCPTool parameters must be valid JSON schema on method ${method.name}")
                    }
                }
            }
        
        // Validate resource methods
        serverClass.methods
            .filter { it.isAnnotationPresent(MCPResource::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(MCPResource::class.java)
                if (annotation.scheme.isBlank()) {
                    errors.add("@MCPResource scheme cannot be blank on method ${method.name}")
                }
                if (annotation.name.isBlank()) {
                    errors.add("@MCPResource name cannot be blank on method ${method.name}")
                }
            }
        
        // Validate prompt methods
        serverClass.methods
            .filter { it.isAnnotationPresent(MCPPrompt::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(MCPPrompt::class.java)
                if (annotation.id.isBlank()) {
                    errors.add("@MCPPrompt id cannot be blank on method ${method.name}")
                }
                if (annotation.name.isBlank()) {
                    errors.add("@MCPPrompt name cannot be blank on method ${method.name}")
                }
            }
        
        return errors
    }
    
    /**
     * Get all tool names from a processed server.
     */
    fun getToolNames(serverInfo: ServerInfo): List<String> {
        return serverInfo.tools.map { it.name }
    }
    
    /**
     * Get all resource schemes from a processed server.
     */
    fun getResourceSchemes(serverInfo: ServerInfo): List<String> {
        return serverInfo.resources.map { it.scheme }.distinct()
    }
    
    /**
     * Get all prompt names from a processed server.
     */
    fun getPromptNames(serverInfo: ServerInfo): List<String> {
        return serverInfo.prompts.map { it.name }
    }
    
    /**
     * Find a specific tool by name.
     */
    fun findTool(serverInfo: ServerInfo, toolName: String): ToolMethodInfo? {
        return serverInfo.tools.find { it.name == toolName }
    }
    
    /**
     * Find resource handlers for a specific scheme.
     */
    fun findResourcesForScheme(serverInfo: ServerInfo, scheme: String): List<ResourceMethodInfo> {
        return serverInfo.resources.filter { it.scheme == scheme }
    }
    
    /**
     * Find a specific prompt by name.
     */
    fun findPrompt(serverInfo: ServerInfo, promptName: String): PromptMethodInfo? {
        return serverInfo.prompts.find { it.name == promptName }
    }
    
    /**
     * Basic JSON validation using bracket counting.
     * This is a simple check - for production use, consider a proper JSON parser.
     */
    private fun isValidBasicJson(json: String): Boolean {
        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escaped = false
        
        for (char in json) {
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' && !escaped -> inString = !inString
                !inString -> {
                    when (char) {
                        '{' -> braceCount++
                        '}' -> braceCount--
                        '[' -> bracketCount++
                        ']' -> bracketCount--
                    }
                    if (braceCount < 0 || bracketCount < 0) return false
                }
            }
        }
        
        return braceCount == 0 && bracketCount == 0 && !inString
    }
}