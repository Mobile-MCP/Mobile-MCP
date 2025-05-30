package io.rosenpin.mmcp.server.annotations

import java.lang.reflect.Method

/**
 * Information about an annotated tool method discovered via reflection.
 * Contains all metadata needed to expose the method as an MCP tool.
 */
data class ToolMethodInfo(
    /**
     * Unique identifier for the tool.
     */
    val id: String,
    
    /**
     * Human-readable name of the tool.
     */
    val name: String,
    
    /**
     * Description of what the tool does.
     */
    val description: String,
    
    /**
     * JSON schema for tool parameters.
     */
    val parametersSchema: String,
    
    /**
     * The Java reflection Method object for this tool.
     */
    val method: Method,
    
    /**
     * Mapping from MCP parameter names to method parameter indices.
     * Used to correctly pass parameters when invoking the method.
     */
    val parameterMapping: Map<String, Int>
) {
    /**
     * Invoke this tool with MCP-compliant arguments object.
     * Follows MCP tools/call format: {name: string, arguments?: object}
     * 
     * @param instance The server instance to invoke the method on
     * @param arguments The arguments object from MCP tools/call request
     * @return The result of the tool invocation
     */
    fun invoke(instance: Any, arguments: Map<String, Any>?): Any? {
        val args = Array<Any?>(method.parameterCount) { null }
        
        arguments?.forEach { (paramName, value) ->
            parameterMapping[paramName]?.let { index ->
                args[index] = value
            }
        }
        
        return method.invoke(instance, *args)
    }
}

/**
 * Information about an annotated resource method discovered via reflection.
 * Contains all metadata needed to expose the method as an MCP resource provider.
 */
data class ResourceMethodInfo(
    /**
     * URI scheme that this resource handler supports.
     */
    val scheme: String,
    
    /**
     * Human-readable name of the resource type.
     */
    val name: String,
    
    /**
     * Description of what resources this handler provides.
     */
    val description: String,
    
    /**
     * Default MIME type for resources.
     */
    val mimeType: String,
    
    /**
     * The Java reflection Method object for this resource handler.
     */
    val method: Method
)

/**
 * Information about an annotated prompt method discovered via reflection.
 * Contains all metadata needed to expose the method as an MCP prompt generator.
 */
data class PromptMethodInfo(
    /**
     * Unique identifier for the prompt.
     */
    val id: String,
    
    /**
     * Human-readable name of the prompt.
     */
    val name: String,
    
    /**
     * Description of what the prompt generates.
     */
    val description: String,
    
    /**
     * JSON schema for prompt parameters.
     */
    val parametersSchema: String,
    
    /**
     * The Java reflection Method object for this prompt generator.
     */
    val method: Method,
    
    /**
     * Mapping from MCP parameter names to method parameter indices.
     */
    val parameterMapping: Map<String, Int>
) {
    /**
     * Invoke this prompt with MCP-compliant arguments object.
     * Follows MCP prompts/get format: {name: string, arguments?: object}
     * 
     * @param instance The server instance to invoke the method on
     * @param arguments The arguments object from MCP prompts/get request
     * @return The prompt result
     */
    fun invoke(instance: Any, arguments: Map<String, Any>?): Any? {
        val args = Array<Any?>(method.parameterCount) { null }
        
        arguments?.forEach { (paramName, value) ->
            parameterMapping[paramName]?.let { index ->
                args[index] = value
            }
        }
        
        return method.invoke(instance, *args)
    }
}

/**
 * Complete information about an MCP server discovered via annotation processing.
 * Contains the server metadata and all discovered capabilities.
 */
data class MCPServerInfo(
    /**
     * Unique server identifier.
     */
    val id: String,
    
    /**
     * Human-readable server name.
     */
    val name: String,
    
    /**
     * Server description.
     */
    val description: String,
    
    /**
     * Server version.
     */
    val version: String,
    
    /**
     * List of MCP capabilities this server supports.
     */
    val capabilities: List<String>,
    
    /**
     * All tool methods discovered in this server.
     */
    val tools: List<ToolMethodInfo> = emptyList(),
    
    /**
     * All resource methods discovered in this server.
     */
    val resources: List<ResourceMethodInfo> = emptyList(),
    
    /**
     * All prompt methods discovered in this server.
     */
    val prompts: List<PromptMethodInfo> = emptyList(),
    
    /**
     * The server class that was processed.
     */
    val serverClass: Class<*>
)