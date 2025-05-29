package io.rosenpin.mmcp.client.protocol

import io.rosenpin.mcp.mmcpcore.protocol.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class JsonRpcSerializerTest {

    private lateinit var serializer: JsonRpcSerializer

    @Before
    fun setup() {
        serializer = JsonRpcSerializer()
    }

    @Test
    fun `serialize request to JSON`() {
        val request = JsonRpcRequest(
            id = "test-123",
            method = "tools/list",
            params = mapOf("category" to "search")
        )

        val json = serializer.serialize(request)

        assertNotNull(json)
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":\"test-123\""))
        assertTrue(json.contains("\"method\":\"tools/list\""))
    }

    @Test
    fun `deserialize request from JSON`() {
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "request-456",
                "method": "tools/call",
                "params": {"name": "calculator", "arguments": {"operation": "add", "a": 5, "b": 3}}
            }
        """.trimIndent()

        val request = serializer.deserializeRequest(json)

        assertEquals("2.0", request.jsonrpc)
        assertEquals("request-456", request.id)
        assertEquals("tools/call", request.method)
        assertNotNull(request.params)
    }

    @Test
    fun `deserialize response from JSON`() {
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "response-789",
                "result": {"value": 8}
            }
        """.trimIndent()

        val response = serializer.deserializeResponse(json)

        assertEquals("2.0", response.jsonrpc)
        assertEquals("response-789", response.id)
        assertNotNull(response.result)
        assertNull(response.error)
    }

    @Test
    fun `deserialize error response from JSON`() {
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "error-101",
                "error": {
                    "code": -32601,
                    "message": "Method not found",
                    "data": {"method": "unknown/method"}
                }
            }
        """.trimIndent()

        val response = serializer.deserializeResponse(json)

        assertEquals("2.0", response.jsonrpc)
        assertEquals("error-101", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32601, response.error?.code)
        assertEquals("Method not found", response.error?.message)
    }

    @Test(expected = Exception::class)
    fun `deserialize malformed JSON throws exception`() {
        val malformedJson = "{ invalid json }"
        serializer.deserializeRequest(malformedJson)
    }

    @Test
    fun `serialize and deserialize round trip preserves data`() {
        val originalRequest = JsonRpcRequest(
            id = "round-trip-test",
            method = "resources/read",
            params = mapOf(
                "uri" to "file://test.txt",
                "options" to mapOf("encoding" to "utf-8")
            )
        )

        val json = serializer.serialize(originalRequest)
        val deserializedRequest = serializer.deserializeRequest(json)

        assertEquals(originalRequest.jsonrpc, deserializedRequest.jsonrpc)
        assertEquals(originalRequest.id, deserializedRequest.id)
        assertEquals(originalRequest.method, deserializedRequest.method)
        assertEquals(originalRequest.params, deserializedRequest.params)
    }
}