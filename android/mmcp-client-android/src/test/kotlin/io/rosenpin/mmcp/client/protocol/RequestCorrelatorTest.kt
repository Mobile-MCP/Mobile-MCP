package io.rosenpin.mmcp.client.protocol

import io.rosenpin.mcp.mmcpcore.protocol.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class RequestCorrelatorTest {

    private lateinit var correlator: RequestCorrelator

    @Before
    fun setup() {
        correlator = RequestCorrelator()
    }

    @Test
    fun `generateRequestId returns unique IDs`() {
        val id1 = correlator.generateRequestId()
        val id2 = correlator.generateRequestId()
        val id3 = correlator.generateRequestId()

        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
        
        // Should be UUID format
        assertTrue(id1.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `createPendingRequest and completeRequest work together`() = runTest {
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
    fun `failRequest completes deferred with exception`() = runTest {
        val requestId = "failed-request-456"
        
        val deferred = correlator.createPendingRequest(requestId)
        assertFalse(deferred.isCompleted)

        val exception = RuntimeException("Request failed")
        correlator.failRequest(requestId, exception)
        
        assertTrue(deferred.isCompleted)
        
        try {
            deferred.await()
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals("Request failed", e.message)
        }
    }

    @Test
    fun `multiple concurrent requests work independently`() = runTest {
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
        
        assertEquals("result1", deferred1.await())
        assertEquals("result2", deferred2.await())
    }

    @Test
    fun `completeRequest with unknown ID does nothing`() {
        val unknownResponse = JsonRpcResponse(
            id = "unknown-request",
            result = "some result",
            error = null
        )
        
        // Should not throw exception
        correlator.completeRequest(unknownResponse)
    }

    @Test
    fun `failRequest with unknown ID does nothing`() {
        val exception = RuntimeException("Test error")
        
        // Should not throw exception
        correlator.failRequest("unknown-request", exception)
    }

    @Test
    fun `request is removed from pending after completion`() = runTest {
        val requestId = "remove-test"
        
        val deferred1 = correlator.createPendingRequest(requestId)
        val response = JsonRpcResponse(id = requestId, result = "result", error = null)
        
        correlator.completeRequest(response)
        assertEquals("result", deferred1.await())
        
        // Creating another request with same ID should work (previous was removed)
        val deferred2 = correlator.createPendingRequest(requestId)
        assertFalse(deferred2.isCompleted)
        
        correlator.completeRequest(response)
        assertEquals("result", deferred2.await())
    }
}