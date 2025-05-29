package io.rosenpin.mmcp.mmcpcore.protocol

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class RequestCorrelatorTest {

    @Test
    fun testGenerateRequestIdReturnsUniqueIds() {
        val correlator = RequestCorrelator()
        
        val id1 = correlator.generateRequestId()
        val id2 = correlator.generateRequestId()
        val id3 = correlator.generateRequestId()

        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
        
        // Should be UUID format (basic check)
        assertTrue(id1.length > 10)
        assertTrue(id1.contains("-"))
    }

    @Test
    fun testCreatePendingRequestAndCompleteRequestWorkTogether() = runTest {
        val correlator = RequestCorrelator()
        val requestId = "test-request-123"
        
        val deferred = correlator.createPendingRequest(requestId)
        assertFalse(deferred.isCompleted)

        val response = JsonRpcResponse(
            id = requestId,
            result = mapOf("success" to true),
            error = null
        )

        correlator.completeRequest(response)
        
        assertTrue(deferred.isCompleted)
        assertEquals(response, deferred.await())
    }

    @Test
    fun testFailRequestCompletesWithException() = runTest {
        val correlator = RequestCorrelator()
        val requestId = "failed-request-456"
        
        val deferred = correlator.createPendingRequest(requestId)
        assertFalse(deferred.isCompleted)

        val exception = RuntimeException("Request failed")
        correlator.failRequest(requestId, exception)
        
        assertTrue(deferred.isCompleted)
        
        assertFailsWith<RuntimeException> {
            deferred.await()
        }
    }

    @Test
    fun testMultipleConcurrentRequestsWorkIndependently() = runTest {
        val correlator = RequestCorrelator()
        val request1Id = "concurrent-1"
        val request2Id = "concurrent-2"
        
        val deferred1 = correlator.createPendingRequest(request1Id)
        val deferred2 = correlator.createPendingRequest(request2Id)
        
        assertFalse(deferred1.isCompleted)
        assertFalse(deferred2.isCompleted)

        // Complete requests in different order
        val response2 = JsonRpcResponse(id = request2Id, result = "result2", error = null)
        val response1 = JsonRpcResponse(id = request1Id, result = "result1", error = null)
        
        correlator.completeRequest(response2)
        assertTrue(deferred2.isCompleted)
        assertFalse(deferred1.isCompleted)
        
        correlator.completeRequest(response1)
        assertTrue(deferred1.isCompleted)
        assertTrue(deferred2.isCompleted)
        
        assertEquals("result1", deferred1.await().result)
        assertEquals("result2", deferred2.await().result)
    }

    @Test
    fun testCompleteRequestWithUnknownIdDoesNothing() {
        val correlator = RequestCorrelator()
        val unknownResponse = JsonRpcResponse(
            id = "unknown-request",
            result = "some result",
            error = null
        )
        
        // Should not throw exception
        correlator.completeRequest(unknownResponse)
    }

    @Test
    fun testFailRequestWithUnknownIdDoesNothing() {
        val correlator = RequestCorrelator()
        val exception = RuntimeException("Test error")
        
        // Should not throw exception
        correlator.failRequest("unknown-request", exception)
    }

    @Test
    fun testRequestIsRemovedFromPendingAfterCompletion() = runTest {
        val correlator = RequestCorrelator()
        val requestId = "remove-test"
        
        val deferred1 = correlator.createPendingRequest(requestId)
        val response = JsonRpcResponse(id = requestId, result = "result", error = null)
        
        assertEquals(1, correlator.getPendingRequestCount())
        
        correlator.completeRequest(response)
        assertEquals("result", deferred1.await().result)
        
        assertEquals(0, correlator.getPendingRequestCount())
        
        // Creating another request with same ID should work (previous was removed)
        val deferred2 = correlator.createPendingRequest(requestId)
        assertFalse(deferred2.isCompleted)
        
        assertEquals(1, correlator.getPendingRequestCount())
        
        correlator.completeRequest(response)
        assertEquals("result", deferred2.await().result)
        
        assertEquals(0, correlator.getPendingRequestCount())
    }

    @Test
    fun testCancelRequest() = runTest {
        val correlator = RequestCorrelator()
        val requestId = "cancel-test"
        
        val deferred = correlator.createPendingRequest(requestId)
        assertFalse(deferred.isCompleted)
        assertEquals(1, correlator.getPendingRequestCount())
        
        correlator.cancelRequest(requestId)
        
        assertTrue(deferred.isCancelled)
        assertEquals(0, correlator.getPendingRequestCount())
    }

    @Test
    fun testCancelAllRequests() = runTest {
        val correlator = RequestCorrelator()
        
        val deferred1 = correlator.createPendingRequest("req1")
        val deferred2 = correlator.createPendingRequest("req2")
        val deferred3 = correlator.createPendingRequest("req3")
        
        assertEquals(3, correlator.getPendingRequestCount())
        
        correlator.cancelAllRequests()
        
        assertTrue(deferred1.isCancelled)
        assertTrue(deferred2.isCancelled)
        assertTrue(deferred3.isCancelled)
        assertEquals(0, correlator.getPendingRequestCount())
    }

    @Test
    fun testIsRequestPending() {
        val correlator = RequestCorrelator()
        val requestId = "pending-test"
        
        assertFalse(correlator.isRequestPending(requestId))
        
        correlator.createPendingRequest(requestId)
        assertTrue(correlator.isRequestPending(requestId))
        
        correlator.cancelRequest(requestId)
        assertFalse(correlator.isRequestPending(requestId))
    }
}