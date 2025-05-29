package io.rosenpin.mmcp.server.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

/**
 * Handles JSON-RPC 2.0 message serialization and deserialization for MCP server
 */
class JsonRpcSerializer {
    
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()
    
    /**
     * Serialize any object to JSON string
     */
    fun serialize(message: Any): String {
        return gson.toJson(message)
    }
    
    /**
     * Deserialize JSON string to JsonRpcRequest
     * @throws JsonSyntaxException if JSON is malformed
     */
    fun deserializeRequest(json: String): JsonRpcRequest {
        try {
            return gson.fromJson(json, JsonRpcRequest::class.java)
                ?: throw JsonSyntaxException("Null result from JSON parsing")
        } catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Failed to parse JSON-RPC request: ${e.message}", e)
        }
    }
    
    /**
     * Create a JSON-RPC error response
     */
    fun createErrorResponse(requestId: String, code: Int, message: String, data: Any? = null): JsonRpcResponse {
        return JsonRpcResponse(
            id = requestId,
            result = null,
            error = JsonRpcError(code, message, data)
        )
    }
    
    /**
     * Create a JSON-RPC success response
     */
    fun createSuccessResponse(requestId: String, result: Any): JsonRpcResponse {
        return JsonRpcResponse(
            id = requestId,
            result = result,
            error = null
        )
    }
}