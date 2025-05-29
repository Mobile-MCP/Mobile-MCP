package io.rosenpin.mmcp.client.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages request/response correlation and timeouts for JSON-RPC messages
 */
class RequestCorrelator(
    private val defaultTimeoutMs: Long = 30_000L, // 30 seconds default timeout
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    
    private data class PendingRequest(
        val deferred: CompletableDeferred<JsonRpcResponse>,
        val timeoutJob: Job
    )
    
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    
    /**
     * Generate a unique request ID
     */
    fun generateRequestId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Create a pending request that will be completed when response arrives
     * @param id Request ID
     * @param timeoutMs Custom timeout in milliseconds (optional)
     * @return CompletableDeferred that will complete with the response
     */
    fun createPendingRequest(
        id: String, 
        timeoutMs: Long = defaultTimeoutMs
    ): CompletableDeferred<JsonRpcResponse> {
        val deferred = CompletableDeferred<JsonRpcResponse>()
        
        // Set up timeout
        val timeoutJob = scope.launch {
            delay(timeoutMs)
            
            // Remove from pending and complete with timeout error
            pendingRequests.remove(id)?.let {
                if (!deferred.isCompleted) {
                    val timeoutError = JsonRpcError(
                        code = JsonRpcError.MCP_TIMEOUT_ERROR,
                        message = "Request timeout after ${timeoutMs}ms",
                        data = mapOf("requestId" to id, "timeoutMs" to timeoutMs)
                    )
                    val timeoutResponse = JsonRpcResponse(
                        id = id,
                        result = null,
                        error = timeoutError
                    )
                    deferred.complete(timeoutResponse)
                }
            }
        }
        
        pendingRequests[id] = PendingRequest(deferred, timeoutJob)
        
        return deferred
    }
    
    /**
     * Complete a pending request with the given response
     * @param response The JSON-RPC response
     */
    fun completeRequest(response: JsonRpcResponse) {
        pendingRequests.remove(response.id)?.let { pendingRequest ->
            // Cancel timeout job
            pendingRequest.timeoutJob.cancel()
            
            // Complete the deferred
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.complete(response)
            }
        }
    }
    
    /**
     * Fail a pending request with the given error
     * @param id Request ID
     * @param error The error that occurred
     */
    fun failRequest(id: String, error: Throwable) {
        pendingRequests.remove(id)?.let { pendingRequest ->
            // Cancel timeout job
            pendingRequest.timeoutJob.cancel()
            
            // Complete exceptionally
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.completeExceptionally(error)
            }
        }
    }
    
    /**
     * Cancel a pending request
     * @param id Request ID
     */
    fun cancelRequest(id: String) {
        pendingRequests.remove(id)?.let { pendingRequest ->
            pendingRequest.timeoutJob.cancel()
            
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.cancel()
            }
        }
    }
    
    /**
     * Get the number of pending requests
     */
    fun getPendingRequestCount(): Int = pendingRequests.size
    
    /**
     * Cancel all pending requests
     */
    fun cancelAllRequests() {
        val requests = pendingRequests.values.toList()
        pendingRequests.clear()
        
        requests.forEach { pendingRequest ->
            pendingRequest.timeoutJob.cancel()
            if (!pendingRequest.deferred.isCompleted) {
                pendingRequest.deferred.cancel()
            }
        }
    }
    
    /**
     * Check if a request is pending
     */
    fun isRequestPending(id: String): Boolean = pendingRequests.containsKey(id)
}