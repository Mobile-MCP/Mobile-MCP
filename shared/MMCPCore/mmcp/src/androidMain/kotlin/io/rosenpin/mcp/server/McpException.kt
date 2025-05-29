package io.rosenpin.mcp.server

/**
 * Exception class for MCP-specific errors
 * This extends RuntimeException to provide MCP-specific error handling
 */
class McpException(
    message: String,
    cause: Throwable? = null,
    val errorCode: Int = ERROR_UNKNOWN,
    val requestId: String? = null
) : RuntimeException(message, cause) {
    
    companion object {
        // Standard JSON-RPC error codes
        const val ERROR_PARSE_ERROR = -32700
        const val ERROR_INVALID_REQUEST = -32600
        const val ERROR_METHOD_NOT_FOUND = -32601
        const val ERROR_INVALID_PARAMS = -32602
        const val ERROR_INTERNAL_ERROR = -32603
        
        // MCP-specific error codes (range -32000 to -32099)
        const val ERROR_MCP_SERVER_ERROR = -32000
        const val ERROR_MCP_TIMEOUT_ERROR = -32001
        const val ERROR_MCP_PERMISSION_DENIED = -32002
        const val ERROR_MCP_SERVICE_UNAVAILABLE = -32003
        const val ERROR_MCP_TOOL_NOT_FOUND = -32004
        const val ERROR_MCP_RESOURCE_NOT_FOUND = -32005
        const val ERROR_MCP_PROMPT_NOT_FOUND = -32006
        const val ERROR_MCP_CAPABILITY_NOT_SUPPORTED = -32007
        
        // Framework-specific error codes (range -32100 to -32199)
        const val ERROR_CONNECTION_FAILED = -32100
        const val ERROR_BINDING_FAILED = -32101
        const val ERROR_SERVICE_NOT_FOUND = -32102
        const val ERROR_UNKNOWN = -32199
    }
    
    /**
     * Create an MCP exception from a JSON-RPC error response
     */
    constructor(errorCode: Int, message: String, requestId: String? = null) : this(
        message = message,
        cause = null,
        errorCode = errorCode,
        requestId = requestId
    )
    
    /**
     * Get a user-friendly error message based on the error code
     */
    fun getUserFriendlyMessage(): String = when (errorCode) {
        ERROR_PARSE_ERROR -> "Failed to parse request"
        ERROR_INVALID_REQUEST -> "Invalid request format"
        ERROR_METHOD_NOT_FOUND -> "Method not found"
        ERROR_INVALID_PARAMS -> "Invalid parameters"
        ERROR_INTERNAL_ERROR -> "Internal server error"
        ERROR_MCP_SERVER_ERROR -> "MCP server error"
        ERROR_MCP_TIMEOUT_ERROR -> "Request timeout"
        ERROR_MCP_PERMISSION_DENIED -> "Permission denied"
        ERROR_MCP_SERVICE_UNAVAILABLE -> "Service unavailable"
        ERROR_MCP_TOOL_NOT_FOUND -> "Tool not found"
        ERROR_MCP_RESOURCE_NOT_FOUND -> "Resource not found"
        ERROR_MCP_PROMPT_NOT_FOUND -> "Prompt not found"
        ERROR_MCP_CAPABILITY_NOT_SUPPORTED -> "Capability not supported"
        ERROR_CONNECTION_FAILED -> "Connection failed"
        ERROR_BINDING_FAILED -> "Service binding failed"
        ERROR_SERVICE_NOT_FOUND -> "Service not found"
        else -> message ?: "Unknown error"
    }
    
    /**
     * Check if this is a client-side error (vs server-side)
     */
    fun isClientError(): Boolean = errorCode in -32199..-32100
    
    /**
     * Check if this is a server-side error
     */
    fun isServerError(): Boolean = errorCode in -32099..-32000 || errorCode in -32603..-32600
}