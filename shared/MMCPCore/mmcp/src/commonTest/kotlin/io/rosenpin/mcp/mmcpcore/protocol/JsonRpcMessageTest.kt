package io.rosenpin.mmcp.mmcpcore.protocol

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse

class JsonRpcMessageTest {

    @Test
    fun testJsonRpcRequestProperties() {
        val request = JsonRpcRequest(
            id = "123",
            method = "tools/list",
            params = mapOf("filter" to "search")
        )

        assertEquals("2.0", request.jsonrpc)
        assertEquals("123", request.id)
        assertEquals("tools/list", request.method)
        assertNotNull(request.params)
    }

    @Test
    fun testJsonRpcRequestWithNullParams() {
        val request = JsonRpcRequest(
            id = "789",
            method = "resources/list",
            params = null
        )

        assertEquals("2.0", request.jsonrpc)
        assertEquals("789", request.id)
        assertEquals("resources/list", request.method)
        assertNull(request.params)
    }

    @Test
    fun testJsonRpcResponseSuccess() {
        val response = JsonRpcResponse(
            id = "123",
            result = mapOf("tools" to listOf("search", "calculator")),
            error = null
        )

        assertEquals("2.0", response.jsonrpc)
        assertEquals("123", response.id)
        assertNotNull(response.result)
        assertNull(response.error)
    }

    @Test
    fun testJsonRpcResponseError() {
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

        assertEquals("2.0", response.jsonrpc)
        assertEquals("456", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32601, response.error?.code)
        assertEquals("Method not found", response.error?.message)
    }

    @Test
    fun testJsonRpcErrorStandardCodes() {
        assertEquals(-32700, JsonRpcError.PARSE_ERROR)
        assertEquals(-32600, JsonRpcError.INVALID_REQUEST)
        assertEquals(-32601, JsonRpcError.METHOD_NOT_FOUND)
        assertEquals(-32602, JsonRpcError.INVALID_PARAMS)
        assertEquals(-32603, JsonRpcError.INTERNAL_ERROR)
        
        // MCP-specific codes
        assertEquals(-32000, JsonRpcError.MCP_SERVER_ERROR)
        assertEquals(-32001, JsonRpcError.MCP_TIMEOUT_ERROR)
        assertEquals(-32002, JsonRpcError.MCP_PERMISSION_DENIED)
    }

    @Test
    fun testMcpMethodConstants() {
        assertEquals("tools/list", McpMethods.TOOLS_LIST)
        assertEquals("tools/call", McpMethods.TOOLS_CALL)
        assertEquals("resources/list", McpMethods.RESOURCES_LIST)
        assertEquals("resources/read", McpMethods.RESOURCES_READ)
        assertEquals("initialize", McpMethods.INITIALIZE)
        assertEquals("ping", McpMethods.PING)
        assertEquals("capabilities", McpMethods.CAPABILITIES)
    }
}