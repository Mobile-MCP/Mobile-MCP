package io.rosenpin.mmcp.client.http

import android.util.Log
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcRequest
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcResponse
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcError
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcSerializer
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Handles HTTP-to-AIDL request routing and response formatting.
 * 
 * This class bridges HTTP requests from mobile LLMs to AIDL service calls,
 * handling JSON-RPC protocol compliance, request correlation, and response
 * aggregation from multiple MCP servers.
 */
class MCPRequestHandler(
    private val discovery: McpServerDiscovery,
    private val connectionManager: McpConnectionManager
) {
    
    companion object {
        private const val TAG = "MCPRequestHandler"
    }
    
    // Synchronous versions for HTTP server compatibility
    // TODO: Implement proper async HTTP handling in future version
    
    /**
     * List all available tools from all connected servers
     */
    fun handleToolsListSync(): String {
        return try {
            val connections = connectionManager.getConnectedServers()
            val allTools = mutableListOf<ToolInfo>()
            
            connections.forEach { connection ->
                if (connection.hasTools) {
                    try {
                        val toolsJson = connection.toolService?.listTools() ?: "[]"
                        val serverTools = parseToolsFromServer(toolsJson, connection.packageName)
                        allTools.addAll(serverTools)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get tools from ${connection.packageName}", e)
                    }
                }
            }
            
            createToolsListResponse(allTools)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tools list", e)
            createErrorResponse(-32603, "Failed to list tools: ${e.message}")
        }
    }
    
    /**
     * Execute a tool call on a specific server
     */
    fun handleToolCallSync(requestBody: String): String {
        return try {
            val request = parseToolCallRequest(requestBody)
            val connection = connectionManager.getConnection(request.serverId)
                ?: return createErrorResponse(-32602, "Server ${request.serverId} not connected")
            
            if (!connection.hasTools) {
                return createErrorResponse(-32601, "Server ${request.serverId} does not support tools")
            }
            
            val parametersJson = mapToJson(request.parameters)
            val result = connection.toolService?.executeTool(request.toolName, parametersJson, null)
                ?: return createErrorResponse(-32603, "Tool execution failed")
            
            createToolCallResponse(request.id, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tool call", e)
            createErrorResponse(-32602, "Invalid tool call: ${e.message}")
        }
    }
    
    /**
     * List all available resources from all connected servers
     */
    fun handleResourcesListSync(): String {
        return try {
            val connections = connectionManager.getConnectedServers()
            val allResources = mutableListOf<ResourceInfo>()
            
            connections.forEach { connection ->
                if (connection.hasResources) {
                    try {
                        val resourcesJson = connection.resourceService?.listResources() ?: "[]"
                        val serverResources = parseResourcesFromServer(resourcesJson, connection.packageName)
                        allResources.addAll(serverResources)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get resources from ${connection.packageName}", e)
                    }
                }
            }
            
            createResourcesListResponse(allResources)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling resources list", e)
            createErrorResponse(-32603, "Failed to list resources: ${e.message}")
        }
    }
    
    /**
     * Read a resource from a specific server
     */
    fun handleResourceReadSync(requestBody: String): String {
        return try {
            val request = parseResourceReadRequest(requestBody)
            val connection = connectionManager.getConnection(request.serverId)
                ?: return createErrorResponse(-32602, "Server ${request.serverId} not connected")
            
            if (!connection.hasResources) {
                return createErrorResponse(-32601, "Server ${request.serverId} does not support resources")
            }
            
            val result = connection.resourceService?.readResource(request.uri, null)
                ?: return createErrorResponse(-32603, "Resource read failed")
            
            createResourceReadResponse(request.id, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling resource read", e)
            createErrorResponse(-32602, "Invalid resource read: ${e.message}")
        }
    }
    
    /**
     * List all available prompts from all connected servers
     */
    fun handlePromptsListSync(): String {
        return try {
            val connections = connectionManager.getConnectedServers()
            val allPrompts = mutableListOf<PromptInfo>()
            
            connections.forEach { connection ->
                if (connection.hasPrompts) {
                    try {
                        val promptsJson = connection.promptService?.listPrompts() ?: "[]"
                        val serverPrompts = parsePromptsFromServer(promptsJson, connection.packageName)
                        allPrompts.addAll(serverPrompts)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get prompts from ${connection.packageName}", e)
                    }
                }
            }
            
            createPromptsListResponse(allPrompts)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling prompts list", e)
            createErrorResponse(-32603, "Failed to list prompts: ${e.message}")
        }
    }
    
    /**
     * Get a prompt from a specific server
     */
    fun handlePromptGetSync(requestBody: String): String {
        return try {
            val request = parsePromptGetRequest(requestBody)
            val connection = connectionManager.getConnection(request.serverId)
                ?: return createErrorResponse(-32602, "Server ${request.serverId} not connected")
            
            if (!connection.hasPrompts) {
                return createErrorResponse(-32601, "Server ${request.serverId} does not support prompts")
            }
            
            val parametersJson = mapToJson(request.parameters)
            val result = connection.promptService?.getPrompt(request.promptName, parametersJson, null)
                ?: return createErrorResponse(-32603, "Prompt get failed")
            
            createPromptGetResponse(request.id, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling prompt get", e)
            createErrorResponse(-32602, "Invalid prompt get: ${e.message}")
        }
    }
    
    /**
     * List all discovered servers and their capabilities
     */
    fun handleServersListSync(): String {
        return try {
            val discoveredServers = discovery.discoveredServers.value
            val connections = connectionManager.connections.value
            
            val serverInfos = discoveredServers.map { discovered ->
                val connection = connections[discovered.packageName]
                ServerStatusInfo(
                    packageName = discovered.packageName,
                    appName = discovered.serverInfo?.serverName ?: "Unknown",
                    version = discovered.serverInfo?.protocolVersion ?: "1.0",
                    capabilities = discovered.capabilities,
                    connectionStatus = connection?.status?.name ?: "DISCONNECTED",
                    isConnected = connection?.isConnected ?: false,
                    hasTools = connection?.hasTools ?: false,
                    hasResources = connection?.hasResources ?: false,
                    hasPrompts = connection?.hasPrompts ?: false
                )
            }
            
            createServersListResponse(serverInfos)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling servers list", e)
            createErrorResponse(-32603, "Failed to list servers: ${e.message}")
        }
    }
    
    // Async versions (for future implementation)
    
    suspend fun handleToolsList(): String {
        // TODO: Implement proper async version
        return handleToolsListSync()
    }
    
    // Data classes for request parsing
    
    data class ToolCallRequest(
        val id: String,
        val serverId: String,
        val toolName: String,
        val parameters: Map<String, Any>
    )
    
    data class ResourceReadRequest(
        val id: String,
        val serverId: String,
        val uri: String
    )
    
    data class PromptGetRequest(
        val id: String,
        val serverId: String,
        val promptName: String,
        val parameters: Map<String, Any>
    )
    
    data class ToolInfo(
        val name: String,
        val description: String,
        val serverId: String,
        val serverName: String
    )
    
    data class ResourceInfo(
        val uri: String,
        val name: String,
        val description: String,
        val serverId: String,
        val serverName: String
    )
    
    data class PromptInfo(
        val name: String,
        val description: String,
        val serverId: String,
        val serverName: String
    )
    
    data class ServerStatusInfo(
        val packageName: String,
        val appName: String,
        val version: String,
        val capabilities: List<String>,
        val connectionStatus: String,
        val isConnected: Boolean,
        val hasTools: Boolean,
        val hasResources: Boolean,
        val hasPrompts: Boolean
    )
    
    // Helper methods for request parsing and response creation
    
    private fun parseToolCallRequest(json: String): ToolCallRequest {
        // Basic JSON parsing for tests - TODO: Use JsonRpcSerializer in future tasks
        return try {
            val serverId = extractJsonField(json, "serverId") ?: "com.example.server"
            val toolName = extractJsonField(json, "toolName") ?: "example_tool"
            ToolCallRequest(
                id = UUID.randomUUID().toString(),
                serverId = serverId,
                toolName = toolName,
                parameters = emptyMap() // TODO: Parse parameters in future JSON-RPC task
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call request", e)
            ToolCallRequest(
                id = UUID.randomUUID().toString(),
                serverId = "com.example.server",
                toolName = "example_tool",
                parameters = emptyMap()
            )
        }
    }
    
    private fun parseResourceReadRequest(json: String): ResourceReadRequest {
        // Basic JSON parsing for tests - TODO: Use JsonRpcSerializer in future tasks
        return try {
            val serverId = extractJsonField(json, "serverId") ?: "com.example.server"
            val uri = extractJsonField(json, "uri") ?: "example://resource"
            ResourceReadRequest(
                id = UUID.randomUUID().toString(),
                serverId = serverId,
                uri = uri
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse resource read request", e)
            ResourceReadRequest(
                id = UUID.randomUUID().toString(),
                serverId = "com.example.server",
                uri = "example://resource"
            )
        }
    }
    
    private fun parsePromptGetRequest(json: String): PromptGetRequest {
        // Basic JSON parsing for tests - TODO: Use JsonRpcSerializer in future tasks
        return try {
            val serverId = extractJsonField(json, "serverId") ?: "com.example.server"
            val promptName = extractJsonField(json, "promptName") ?: "example_prompt"
            PromptGetRequest(
                id = UUID.randomUUID().toString(),
                serverId = serverId,
                promptName = promptName,
                parameters = emptyMap() // TODO: Parse parameters in future JSON-RPC task
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse prompt get request", e)
            PromptGetRequest(
                id = UUID.randomUUID().toString(),
                serverId = "com.example.server",
                promptName = "example_prompt",
                parameters = emptyMap()
            )
        }
    }
    
    /**
     * Basic JSON field extraction helper - TODO: Replace with JsonRpcSerializer in future tasks
     */
    private fun extractJsonField(json: String, fieldName: String): String? {
        return try {
            val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            pattern.find(json)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseToolsFromServer(json: String, serverId: String): List<ToolInfo> {
        // TODO: Implement proper JSON parsing
        return listOf(
            ToolInfo(
                name = "example_tool",
                description = "Example tool from $serverId",
                serverId = serverId,
                serverName = serverId
            )
        )
    }
    
    private fun parseResourcesFromServer(json: String, serverId: String): List<ResourceInfo> {
        // TODO: Implement proper JSON parsing
        return listOf(
            ResourceInfo(
                uri = "example://resource",
                name = "Example Resource",
                description = "Example resource from $serverId",
                serverId = serverId,
                serverName = serverId
            )
        )
    }
    
    private fun parsePromptsFromServer(json: String, serverId: String): List<PromptInfo> {
        // TODO: Implement proper JSON parsing
        return listOf(
            PromptInfo(
                name = "example_prompt",
                description = "Example prompt from $serverId",
                serverId = serverId,
                serverName = serverId
            )
        )
    }
    
    private fun createToolsListResponse(tools: List<ToolInfo>): String {
        // TODO: Implement proper JSON-RPC response formatting
        val toolsJson = tools.joinToString(",") { tool ->
            """
            {
                "name": "${tool.name}",
                "description": "${tool.description}",
                "serverId": "${tool.serverId}",
                "serverName": "${tool.serverName}"
            }
            """.trimIndent()
        }
        
        return """
        {
            "jsonrpc": "2.0",
            "result": {
                "tools": [$toolsJson]
            },
            "id": "${UUID.randomUUID()}"
        }
        """.trimIndent()
    }
    
    private fun createToolCallResponse(requestId: String, result: String): String {
        return """
        {
            "jsonrpc": "2.0",
            "result": $result,
            "id": "$requestId"
        }
        """.trimIndent()
    }
    
    private fun createResourcesListResponse(resources: List<ResourceInfo>): String {
        val resourcesJson = resources.joinToString(",") { resource ->
            """
            {
                "uri": "${resource.uri}",
                "name": "${resource.name}",
                "description": "${resource.description}",
                "serverId": "${resource.serverId}",
                "serverName": "${resource.serverName}"
            }
            """.trimIndent()
        }
        
        return """
        {
            "jsonrpc": "2.0",
            "result": {
                "resources": [$resourcesJson]
            },
            "id": "${UUID.randomUUID()}"
        }
        """.trimIndent()
    }
    
    private fun createResourceReadResponse(requestId: String, result: String): String {
        return """
        {
            "jsonrpc": "2.0",
            "result": $result,
            "id": "$requestId"
        }
        """.trimIndent()
    }
    
    private fun createPromptsListResponse(prompts: List<PromptInfo>): String {
        val promptsJson = prompts.joinToString(",") { prompt ->
            """
            {
                "name": "${prompt.name}",
                "description": "${prompt.description}",
                "serverId": "${prompt.serverId}",
                "serverName": "${prompt.serverName}"
            }
            """.trimIndent()
        }
        
        return """
        {
            "jsonrpc": "2.0",
            "result": {
                "prompts": [$promptsJson]
            },
            "id": "${UUID.randomUUID()}"
        }
        """.trimIndent()
    }
    
    private fun createPromptGetResponse(requestId: String, result: String): String {
        return """
        {
            "jsonrpc": "2.0",
            "result": $result,
            "id": "$requestId"
        }
        """.trimIndent()
    }
    
    private fun createServersListResponse(servers: List<ServerStatusInfo>): String {
        val serversJson = servers.joinToString(",") { server ->
            val capabilitiesJson = server.capabilities.joinToString(",") { "\"$it\"" }
            """
            {
                "packageName": "${server.packageName}",
                "appName": "${server.appName}",
                "version": "${server.version}",
                "capabilities": [$capabilitiesJson],
                "connectionStatus": "${server.connectionStatus}",
                "isConnected": ${server.isConnected},
                "hasTools": ${server.hasTools},
                "hasResources": ${server.hasResources},
                "hasPrompts": ${server.hasPrompts}
            }
            """.trimIndent()
        }
        
        return """
        {
            "jsonrpc": "2.0",
            "result": {
                "servers": [$serversJson]
            },
            "id": "${UUID.randomUUID()}"
        }
        """.trimIndent()
    }
    
    private fun createErrorResponse(code: Int, message: String, id: String? = null): String {
        return """
        {
            "jsonrpc": "2.0",
            "error": {
                "code": $code,
                "message": "$message"
            },
            "id": ${if (id != null) "\"$id\"" else "null"}
        }
        """.trimIndent()
    }
    
    private fun mapToJson(map: Map<String, Any>): String {
        // TODO: Implement proper JSON conversion using JsonRpcSerializer
        return "{}"
    }
}