package io.rosenpin.mmcp.mmcpcore.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonRpcSerializerTest {

    private val serializer = JsonRpcSerializer()

    @Test
    fun testCreateSuccessResponse() {
        val response = serializer.createSuccessResponse(
            requestId = "test-123",
            result = mapOf("status" to "ok", "data" to listOf(1, 2, 3))
        )

        assertEquals("2.0", response.jsonrpc)
        assertEquals("test-123", response.id)
        assertNotNull(response.result)
        assertNull(response.error)
    }

    @Test
    fun testCreateErrorResponse() {
        val response = serializer.createErrorResponse(
            requestId = "error-456",
            code = JsonRpcError.METHOD_NOT_FOUND,
            message = "Method not found",
            data = mapOf("method" to "unknown/method")
        )

        assertEquals("2.0", response.jsonrpc)
        assertEquals("error-456", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error?.code)
        assertEquals("Method not found", response.error?.message)
        assertNotNull(response.error?.data)
    }

    @Test
    fun testCreateErrorResponseWithoutData() {
        val response = serializer.createErrorResponse(
            requestId = "simple-error",
            code = JsonRpcError.INTERNAL_ERROR,
            message = "Internal server error"
        )

        assertEquals("2.0", response.jsonrpc)
        assertEquals("simple-error", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(JsonRpcError.INTERNAL_ERROR, response.error?.code)
        assertEquals("Internal server error", response.error?.message)
        assertNull(response.error?.data)
    }

    @Test
    fun testSerializeRequest() {
        val request = JsonRpcRequest(
            id = "serialize-test",
            method = "tools/list",
            params = mapOf("category" to "search")
        )

        val json = serializer.serialize(request)

        assertNotNull(json)
        assertTrue(json.isNotEmpty())
        // Basic JSON structure checks - platform implementation details vary
        assertTrue(json.contains("jsonrpc") || json.contains("\"jsonrpc\""))
        assertTrue(json.contains("serialize-test"))
        assertTrue(json.contains("tools/list"))
    }

    @Test
    fun testSerializeResponse() {
        val response = JsonRpcResponse(
            id = "response-test",
            result = mapOf("success" to true),
            error = null
        )

        val json = serializer.serialize(response)

        assertNotNull(json)
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("jsonrpc") || json.contains("\"jsonrpc\""))
        assertTrue(json.contains("response-test"))
    }
}