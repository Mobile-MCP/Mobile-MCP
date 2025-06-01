package io.rosenpin.mmcp.client.http

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * HTTP server that exposes MCP functionality over REST API.
 * 
 * This server bridges HTTP requests from mobile LLMs (like Ollama) to AIDL-based
 * MCP servers on the device. It provides a standard REST interface that mobile
 * LLMs can easily consume while handling the complexity of service discovery
 * and AIDL communication underneath.
 * 
 * Endpoints:
 * - GET /mcp/tools/list - List all available tools from all servers
 * - POST /mcp/tools/call - Execute a tool on a specific server
 * - GET /mcp/resources/list - List all available resources from all servers  
 * - POST /mcp/resources/read - Read a resource from a specific server
 * - GET /mcp/prompts/list - List all available prompts from all servers
 * - POST /mcp/prompts/get - Get a prompt from a specific server
 * - GET /mcp/servers - List all discovered servers and their capabilities
 */
class MCPHttpServer(
    private val port: Int = 11434,
    discovery: McpServerDiscovery,
    connectionManager: McpConnectionManager
) : NanoHTTPD(port) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestHandler = MCPRequestHandler(discovery, connectionManager)
    
    companion object {
        private const val TAG = "MCPHttpServer"
        const val DEFAULT_PORT = 11434
        
        // HTTP headers
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin"
        private const val HEADER_ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods"
        private const val HEADER_ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers"
        
        // MIME types
        private const val MIME_JSON = "application/json"
    }
    
    override fun serve(session: IHTTPSession): Response {
        return try {
            Log.d(TAG, "Received ${session.method} request to ${session.uri}")
            
            // Handle CORS preflight
            if (session.method == Method.OPTIONS) {
                return createCorsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
            }
            
            // Route the request
            val response = when {
                session.uri.startsWith("/mcp/tools/list") && session.method == Method.GET -> {
                    handleToolsList(session)
                }
                session.uri.startsWith("/mcp/tools/call") && session.method == Method.POST -> {
                    handleToolCall(session)
                }
                session.uri.startsWith("/mcp/resources/list") && session.method == Method.GET -> {
                    handleResourcesList(session)
                }
                session.uri.startsWith("/mcp/resources/read") && session.method == Method.POST -> {
                    handleResourceRead(session)
                }
                session.uri.startsWith("/mcp/prompts/list") && session.method == Method.GET -> {
                    handlePromptsList(session)
                }
                session.uri.startsWith("/mcp/prompts/get") && session.method == Method.POST -> {
                    handlePromptGet(session)
                }
                session.uri.startsWith("/mcp/servers") && session.method == Method.GET -> {
                    handleServersList(session)
                }
                session.uri == "/health" && session.method == Method.GET -> {
                    handleHealthCheck()
                }
                else -> {
                    createErrorResponse(
                        Response.Status.NOT_FOUND,
                        -32601, // Method not found
                        "Endpoint not found: ${session.method} ${session.uri}"
                    )
                }
            }
            
            createCorsResponse(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request ${session.uri}", e)
            createCorsResponse(
                createErrorResponse(
                    Response.Status.INTERNAL_ERROR,
                    -32603, // Internal error
                    "Internal server error: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Handle GET /mcp/tools/list
     */
    private fun handleToolsList(session: IHTTPSession): Response {
        return scope.launch {
            try {
                val result = requestHandler.handleToolsList()
                newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing tools", e)
                createErrorResponse(
                    Response.Status.INTERNAL_ERROR,
                    -32603,
                    "Failed to list tools: ${e.message}"
                )
            }
        }.let {
            // Return a placeholder for now - proper async handling will be implemented
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, requestHandler.handleToolsListSync())
        }
    }
    
    /**
     * Handle POST /mcp/tools/call
     */
    private fun handleToolCall(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val result = requestHandler.handleToolCallSync(body)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool", e)
            createErrorResponse(
                Response.Status.BAD_REQUEST,
                -32602,
                "Invalid tool call: ${e.message}"
            )
        }
    }
    
    /**
     * Handle GET /mcp/resources/list
     */
    private fun handleResourcesList(session: IHTTPSession): Response {
        return try {
            val result = requestHandler.handleResourcesListSync()
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing resources", e)
            createErrorResponse(
                Response.Status.INTERNAL_ERROR,
                -32603,
                "Failed to list resources: ${e.message}"
            )
        }
    }
    
    /**
     * Handle POST /mcp/resources/read
     */
    private fun handleResourceRead(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val result = requestHandler.handleResourceReadSync(body)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading resource", e)
            createErrorResponse(
                Response.Status.BAD_REQUEST,
                -32602,
                "Invalid resource read: ${e.message}"
            )
        }
    }
    
    /**
     * Handle GET /mcp/prompts/list
     */
    private fun handlePromptsList(session: IHTTPSession): Response {
        return try {
            val result = requestHandler.handlePromptsListSync()
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing prompts", e)
            createErrorResponse(
                Response.Status.INTERNAL_ERROR,
                -32603,
                "Failed to list prompts: ${e.message}"
            )
        }
    }
    
    /**
     * Handle POST /mcp/prompts/get
     */
    private fun handlePromptGet(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val result = requestHandler.handlePromptGetSync(body)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting prompt", e)
            createErrorResponse(
                Response.Status.BAD_REQUEST,
                -32602,
                "Invalid prompt get: ${e.message}"
            )
        }
    }
    
    /**
     * Handle GET /mcp/servers
     */
    private fun handleServersList(session: IHTTPSession): Response {
        return try {
            val result = requestHandler.handleServersListSync()
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing servers", e)
            createErrorResponse(
                Response.Status.INTERNAL_ERROR,
                -32603,
                "Failed to list servers: ${e.message}"
            )
        }
    }
    
    /**
     * Handle GET /health
     */
    private fun handleHealthCheck(): Response {
        val healthData = """
            {
                "status": "healthy",
                "port": $port,
                "timestamp": ${System.currentTimeMillis()},
                "version": "1.0.0"
            }
        """.trimIndent()
        
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, healthData)
    }
    
    /**
     * Extract request body from POST requests
     */
    private fun getRequestBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength == 0) {
            throw IllegalArgumentException("Request body is required")
        }
        
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        
        return files["postData"] ?: throw IllegalArgumentException("No request body found")
    }
    
    /**
     * Create a JSON-RPC error response
     */
    private fun createErrorResponse(
        httpStatus: Response.Status,
        rpcErrorCode: Int,
        message: String,
        id: String? = null
    ): Response {
        val errorResponse = """
            {
                "jsonrpc": "2.0",
                "error": {
                    "code": $rpcErrorCode,
                    "message": "$message"
                },
                "id": ${if (id != null) "\"$id\"" else "null"}
            }
        """.trimIndent()
        
        return newFixedLengthResponse(httpStatus, MIME_JSON, errorResponse)
    }
    
    /**
     * Add CORS headers to response
     */
    private fun createCorsResponse(response: Response): Response {
        response.addHeader(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        response.addHeader(HEADER_ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
        response.addHeader(HEADER_ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization")
        return response
    }
    
    /**
     * Start the HTTP server
     */
    fun startServer(): Result<Unit> {
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "MCP HTTP Server started on port $port")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server on port $port", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop the HTTP server
     */
    fun stopServer() {
        try {
            stop()
            scope.cancel()
            Log.i(TAG, "MCP HTTP Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping HTTP server", e)
        }
    }
    
    /**
     * Get server status
     */
    fun isRunning(): Boolean = isAlive
    fun getPort(): Int = port
}