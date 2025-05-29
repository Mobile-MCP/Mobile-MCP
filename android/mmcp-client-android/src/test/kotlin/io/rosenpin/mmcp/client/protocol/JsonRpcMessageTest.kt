package io.rosenpin.mmcp.client.protocol

import com.google.gson.Gson
import org.junit.Test
import org.junit.Assert.*

class JsonRpcMessageTest {

    private val gson = Gson()

    @Test
    fun `JsonRpcRequest serializes correctly`() {
        val request = JsonRpcRequest(
            id = "123",
            method = "tools/list",
            params = mapOf("filter" to "search")
        )

        val json = gson.toJson(request)
        
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":\"123\""))
        assertTrue(json.contains("\"method\":\"tools/list\""))
        assertTrue(json.contains("\"params\""))
    }

    @Test
    fun `JsonRpcRequest deserializes correctly`() {
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "456",
                "method": "tools/call",
                "params": {"name": "search", "arguments": {"query": "test"}}
            }
        """.trimIndent()

        val request = gson.fromJson(json, JsonRpcRequest::class.java)

        assertEquals("2.0", request.jsonrpc)
        assertEquals("456", request.id)
        assertEquals("tools/call", request.method)
        assertNotNull(request.params)
    }

    @Test
    fun `JsonRpcRequest with null params`() {
        val request = JsonRpcRequest(
            id = "789",
            method = "resources/list",
            params = null
        )

        val json = gson.toJson(request)
        val deserialized = gson.fromJson(json, JsonRpcRequest::class.java)

        assertEquals(request.id, deserialized.id)
        assertEquals(request.method, deserialized.method)
        assertEquals(request.params, deserialized.params)
    }

    @Test
    fun `JsonRpcResponse success serializes correctly`() {
        val response = JsonRpcResponse(
            id = "123",
            result = mapOf("tools" to listOf("search", "calculator")),
            error = null
        )

        val json = gson.toJson(response)
        
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":\"123\""))
        assertTrue(json.contains("\"result\""))
        assertFalse(json.contains("\"error\""))
    }

    @Test
    fun `JsonRpcResponse error serializes correctly`() {
        val error = JsonRpcError(
            code = -32601,
            message = "Method not found",
            data = mapOf("method" to "unknown/method")
        )
        
        val response = JsonRpcResponse(
            id = "456",
            result = null,
            error = error
        )

        val json = gson.toJson(response)
        
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":\"456\""))
        assertTrue(json.contains("\"error\""))
        assertTrue(json.contains("\"code\":-32601"))
        assertTrue(json.contains("\"message\":\"Method not found\""))
        assertFalse(json.contains("\"result\""))
    }

    @Test
    fun `JsonRpcError standard error codes`() {
        assertEquals(-32700, JsonRpcError.PARSE_ERROR)
        assertEquals(-32600, JsonRpcError.INVALID_REQUEST)
        assertEquals(-32601, JsonRpcError.METHOD_NOT_FOUND)
        assertEquals(-32602, JsonRpcError.INVALID_PARAMS)
        assertEquals(-32603, JsonRpcError.INTERNAL_ERROR)
    }
}