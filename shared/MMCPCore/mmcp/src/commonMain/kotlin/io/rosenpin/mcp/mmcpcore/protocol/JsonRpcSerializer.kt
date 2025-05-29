package io.rosenpin.mcp.mmcpcore.protocol

/**
 * Cross-platform JSON-RPC 2.0 message serialization interface
 * Platform-specific implementations will handle actual JSON serialization
 */
expect class JsonRpcSerializer() {
    
    /**
     * Serialize any object to JSON string
     */
    fun serialize(message: Any): String
    
    /**
     * Deserialize JSON string to JsonRpcRequest
     * @throws Exception if JSON is malformed
     */
    fun deserializeRequest(json: String): JsonRpcRequest
    
    /**
     * Deserialize JSON string to JsonRpcResponse
     * @throws Exception if JSON is malformed
     */
    fun deserializeResponse(json: String): JsonRpcResponse
    
    /**
     * Try to deserialize as either request or response
     * @return Pair<JsonRpcRequest?, JsonRpcResponse?> where exactly one will be non-null
     */
    fun deserializeMessage(json: String): Pair<JsonRpcRequest?, JsonRpcResponse?>
    
    /**
     * Create a JSON-RPC error response
     */
    fun createErrorResponse(requestId: String, code: Int, message: String, data: Any? = null): JsonRpcResponse
    
    /**
     * Create a JSON-RPC success response
     */
    fun createSuccessResponse(requestId: String, result: Any): JsonRpcResponse
}