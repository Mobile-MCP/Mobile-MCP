package io.rosenpin.mmcp.client.protocol

import kotlinx.coroutines.CompletableDeferred

/**
 * Core MCP protocol handler that manages the protocol state machine
 */
class McpProtocolCore {
    
    private val serializer = JsonRpcSerializer()
    private val correlator = RequestCorrelator()
    
    /**
     * Process an incoming JSON-RPC message
     * @param jsonMessage Raw JSON string
     * @return Response JSON string if this was a request, null if it was a response
     */
    fun processMessage(jsonMessage: String): String? {
        return try {
            val (request, response) = serializer.deserializeMessage(jsonMessage)
            
            when {
                request != null -> {
                    // This is a request - process it and return response
                    processRequest(request)
                }
                response != null -> {
                    // This is a response - correlate it with pending request
                    correlator.completeRequest(response)
                    null
                }
                else -> {
                    // Should not happen
                    val errorResponse = serializer.createErrorResponse(
                        requestId = "unknown",
                        code = JsonRpcError.INVALID_REQUEST,
                        message = "Could not determine message type"
                    )
                    serializer.serialize(errorResponse)
                }
            }
        } catch (e: Exception) {
            // JSON parsing failed
            val errorResponse = serializer.createErrorResponse(
                requestId = "unknown",
                code = JsonRpcError.PARSE_ERROR,
                message = "Parse error: ${e.message}"
            )
            serializer.serialize(errorResponse)
        }
    }
    
    /**
     * Process a JSON-RPC request and return response JSON
     */
    private fun processRequest(request: JsonRpcRequest): String {
        return try {
            when (request.method) {
                McpMethods.PING -> {
                    val response = serializer.createSuccessResponse(
                        requestId = request.id,
                        result = mapOf("status" to "pong")
                    )
                    serializer.serialize(response)
                }
                
                McpMethods.CAPABILITIES -> {
                    val capabilities = mapOf(
                        "tools" to mapOf("listChanged" to true),
                        "resources" to mapOf("subscribe" to true, "listChanged" to true)
                    )
                    val response = serializer.createSuccessResponse(
                        requestId = request.id,
                        result = capabilities
                    )
                    serializer.serialize(response)
                }
                
                else -> {
                    // Method not implemented in core - should be handled by specific implementations
                    val errorResponse = serializer.createErrorResponse(
                        requestId = request.id,
                        code = JsonRpcError.METHOD_NOT_FOUND,
                        message = "Method '${request.method}' not found in core protocol",
                        data = mapOf("method" to request.method)
                    )
                    serializer.serialize(errorResponse)
                }
            }
        } catch (e: Exception) {
            val errorResponse = serializer.createErrorResponse(
                requestId = request.id,
                code = JsonRpcError.INTERNAL_ERROR,
                message = "Internal error: ${e.message}"
            )
            serializer.serialize(errorResponse)
        }
    }
    
    /**
     * Create a new request and return the pending deferred
     */
    fun createRequest(
        method: String, 
        params: Map<String, Any>? = null,
        timeoutMs: Long = 30_000L
    ): Pair<String, CompletableDeferred<JsonRpcResponse>> {
        val requestId = correlator.generateRequestId()
        val request = JsonRpcRequest(
            id = requestId,
            method = method,
            params = params
        )
        
        val requestJson = serializer.serialize(request)
        val deferred = correlator.createPendingRequest(requestId, timeoutMs)
        
        return Pair(requestJson, deferred)
    }
    
    /**
     * Get pending request count
     */
    fun getPendingRequestCount(): Int = correlator.getPendingRequestCount()
    
    /**
     * Cancel all pending requests
     */
    fun shutdown() {
        correlator.cancelAllRequests()
    }
}