package io.rosenpin.mmcp.mmcpcore.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

/**
 * Android implementation of JsonRpcSerializer using Gson
 */
actual class JsonRpcSerializer {
    
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()
    
    /**
     * Serialize any object to JSON string
     */
    actual fun serialize(message: Any): String {
        return gson.toJson(message)
    }
    
    /**
     * Deserialize JSON string to JsonRpcRequest
     * @throws JsonSyntaxException if JSON is malformed
     */
    actual fun deserializeRequest(json: String): JsonRpcRequest {
        try {
            return gson.fromJson(json, JsonRpcRequest::class.java)
                ?: throw JsonSyntaxException("Null result from JSON parsing")
        } catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Failed to parse JSON-RPC request: ${e.message}", e)
        }
    }
    
    /**
     * Deserialize JSON string to JsonRpcResponse
     * @throws JsonSyntaxException if JSON is malformed
     */
    actual fun deserializeResponse(json: String): JsonRpcResponse {
        try {
            return gson.fromJson(json, JsonRpcResponse::class.java)
                ?: throw JsonSyntaxException("Null result from JSON parsing")
        } catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Failed to parse JSON-RPC response: ${e.message}", e)
        }
    }
    
    /**
     * Try to deserialize as either request or response
     * @return Pair<JsonRpcRequest?, JsonRpcResponse?> where exactly one will be non-null
     */
    actual fun deserializeMessage(json: String): Pair<JsonRpcRequest?, JsonRpcResponse?> {
        return try {
            // Try as response first (has result or error field)
            if (json.contains("\"result\"") || json.contains("\"error\"")) {
                Pair(null, deserializeResponse(json))
            } else {
                Pair(deserializeRequest(json), null)
            }
        } catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Failed to parse JSON-RPC message: ${e.message}", e)
        }
    }
    
    /**
     * Create a JSON-RPC error response
     */
    actual fun createErrorResponse(requestId: String, code: Int, message: String, data: Any?): JsonRpcResponse {
        return JsonRpcResponse(
            id = requestId,
            result = null,
            error = JsonRpcError(code, message, data)
        )
    }
    
    /**
     * Create a JSON-RPC success response
     */
    actual fun createSuccessResponse(requestId: String, result: Any): JsonRpcResponse {
        return JsonRpcResponse(
            id = requestId,
            result = result,
            error = null
        )
    }
}