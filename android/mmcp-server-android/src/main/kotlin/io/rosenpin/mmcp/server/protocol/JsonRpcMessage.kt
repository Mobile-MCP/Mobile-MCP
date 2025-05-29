package io.rosenpin.mmcp.server.protocol

/**
 * JSON-RPC 2.0 Request message
 */
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: Map<String, Any>?
)

/**
 * JSON-RPC 2.0 Response message
 */
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: Any?,
    val error: JsonRpcError?
)

/**
 * JSON-RPC 2.0 Error object
 */
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any?
) {
    companion object {
        // Standard JSON-RPC 2.0 error codes
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
        
        // MCP-specific error codes (range -32000 to -32099)
        const val MCP_SERVER_ERROR = -32000
        const val MCP_TIMEOUT_ERROR = -32001
        const val MCP_PERMISSION_DENIED = -32002
    }
}

/**
 * Standard MCP method names
 */
object McpMethods {
    const val TOOLS_LIST = "tools/list"
    const val TOOLS_CALL = "tools/call"
    const val RESOURCES_LIST = "resources/list"
    const val RESOURCES_READ = "resources/read"
    const val INITIALIZE = "initialize"
    const val PING = "ping"
    const val CAPABILITIES = "capabilities"
}