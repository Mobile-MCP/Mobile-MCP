package io.rosenpin.mcp.mmcpcore.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.random.Random

/**
 * Manages request/response correlation and timeouts for JSON-RPC messages
 * Uses KMP coroutines for cross-platform async support
 */
class RequestCorrelator(
    private val defaultTimeoutMs: Long = 30_000L, // 30 seconds default timeout
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    
    private data class PendingRequest(
        val deferred: CompletableDeferred<JsonRpcResponse>,
        val timeoutJob: Job
    )
    
    // Using a simple Map for KMP compatibility instead of ConcurrentHashMap  
    @Volatile
    private var pendingRequests = mutableMapOf<String, PendingRequest>()
    
    /**
     * Generate a unique request ID using random numbers
     */
    fun generateRequestId(): String {
        return "req-${Random.nextLong()}-${Random.nextInt(10000, 99999)}"
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
            val pendingRequest = pendingRequests.remove(id)
            pendingRequest?.let {
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
        val pendingRequest = pendingRequests.remove(response.id)
        
        pendingRequest?.let { 
            // Cancel timeout job
            it.timeoutJob.cancel()
            
            // Complete the deferred
            if (!it.deferred.isCompleted) {
                it.deferred.complete(response)
            }
        }
    }
    
    /**
     * Fail a pending request with the given error
     * @param id Request ID
     * @param error The error that occurred
     */
    fun failRequest(id: String, error: Throwable) {
        val pendingRequest = pendingRequests.remove(id)
        
        pendingRequest?.let {
            // Cancel timeout job
            it.timeoutJob.cancel()
            
            // Complete exceptionally
            if (!it.deferred.isCompleted) {
                it.deferred.completeExceptionally(error)
            }
        }
    }
    
    /**
     * Cancel a pending request
     * @param id Request ID
     */
    fun cancelRequest(id: String) {
        val pendingRequest = pendingRequests.remove(id)
        
        pendingRequest?.let {
            it.timeoutJob.cancel()
            
            if (!it.deferred.isCompleted) {
                it.deferred.cancel()
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
        val copy = pendingRequests.values.toList()
        pendingRequests.clear()
        
        copy.forEach { pendingRequest ->
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