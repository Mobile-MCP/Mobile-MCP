package io.rosenpin.mmcp.server.annotations

/**
 * Annotation to mark a class as an MCP server.
 * This annotation provides metadata about the server's identity and capabilities.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class MCPServer(
    /**
     * Unique identifier for this MCP server.
     * Should follow reverse domain naming convention (e.g., "com.example.myserver").
     */
    val id: String,
    
    /**
     * Human-readable name of the server.
     */
    val name: String,
    
    /**
     * Description of what this server provides.
     */
    val description: String,
    
    /**
     * Version of the server implementation.
     */
    val version: String
)

/**
 * Annotation to mark a method as an MCP tool.
 * The annotated method will be exposed as a callable tool through MCP.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class MCPTool(
    /**
     * Unique identifier for this tool within the server.
     */
    val id: String,
    
    /**
     * Human-readable name of the tool.
     */
    val name: String,
    
    /**
     * Description of what this tool does.
     */
    val description: String,
    
    /**
     * JSON schema describing the tool's parameters.
     * Should be a valid JSON Schema object definition.
     * Example: """{"type": "object", "properties": {"input": {"type": "string"}}}"""
     */
    val parameters: String = "{}"
)

/**
 * Annotation to mark a method as providing MCP resources.
 * The annotated method will handle resource requests for the specified URI scheme.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class MCPResource(
    /**
     * URI scheme that this resource handler supports (e.g., "file", "http", "custom").
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
     * Default MIME type for resources provided by this handler.
     */
    val mimeType: String = "application/octet-stream"
)

/**
 * Annotation to mark method parameters that should be mapped from MCP tool/resource calls.
 * This allows explicit mapping of MCP parameters to method arguments.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class MCPParam(
    /**
     * Name of the parameter as it appears in MCP requests.
     * This name will be used to extract the value from the MCP call parameters.
     */
    val name: String,
    
    /**
     * Optional description of what this parameter represents.
     */
    val description: String = ""
)

/**
 * Annotation to mark a method as providing MCP prompts.
 * The annotated method will be exposed as a prompt generator through MCP.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class MCPPrompt(
    /**
     * Unique identifier for this prompt within the server.
     */
    val id: String,
    
    /**
     * Human-readable name of the prompt.
     */
    val name: String,
    
    /**
     * Description of what this prompt generates.
     */
    val description: String,
    
    /**
     * JSON schema describing the prompt's parameters.
     * Should be a valid JSON Schema object definition.
     */
    val parameters: String = "{}"
)