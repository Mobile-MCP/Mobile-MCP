package io.rosenpin.mmcp.client.http

import android.util.Log
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcRequest
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcResponse
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcError
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcSerializer
import io.rosenpin.mmcp.mmcpcore.protocol.McpMethods
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
    
    private val jsonRpcSerializer = JsonRpcSerializer()
    
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
        val (request, _) = jsonRpcSerializer.deserializeMessage(json)
        if (request == null || request.method != McpMethods.TOOLS_CALL) {
            throw IllegalArgumentException("Invalid JSON-RPC request or method")
        }
        
        val params = request.params ?: throw IllegalArgumentException("Missing required 'params' field")
        
        val serverId = params["serverId"] as? String
            ?: throw IllegalArgumentException("Missing required 'serverId' parameter")
        
        val name = params["name"] as? String
            ?: throw IllegalArgumentException("Missing required 'name' parameter")
        
        val arguments = params["arguments"] as? Map<String, Any> ?: emptyMap()
        
        return ToolCallRequest(
            id = request.id,
            serverId = serverId,
            toolName = name,
            parameters = arguments
        )
    }
    
    private fun parseResourceReadRequest(json: String): ResourceReadRequest {
        val (request, _) = jsonRpcSerializer.deserializeMessage(json)
        if (request == null || request.method != McpMethods.RESOURCES_READ) {
            throw IllegalArgumentException("Invalid JSON-RPC request or method")
        }
        
        val params = request.params ?: throw IllegalArgumentException("Missing required 'params' field")
        
        val serverId = params["serverId"] as? String
            ?: throw IllegalArgumentException("Missing required 'serverId' parameter")
        
        val uri = params["uri"] as? String
            ?: throw IllegalArgumentException("Missing required 'uri' parameter")
        
        return ResourceReadRequest(
            id = request.id,
            serverId = serverId,
            uri = uri
        )
    }
    
    private fun parsePromptGetRequest(json: String): PromptGetRequest {
        val (request, _) = jsonRpcSerializer.deserializeMessage(json)
        if (request == null || request.method != McpMethods.PROMPTS_GET) {
            throw IllegalArgumentException("Invalid JSON-RPC request or method")
        }
        
        val params = request.params ?: throw IllegalArgumentException("Missing required 'params' field")
        
        val serverId = params["serverId"] as? String
            ?: throw IllegalArgumentException("Missing required 'serverId' parameter")
        
        val name = params["name"] as? String
            ?: throw IllegalArgumentException("Missing required 'name' parameter")
        
        val arguments = params["arguments"] as? Map<String, Any> ?: emptyMap()
        
        return PromptGetRequest(
            id = request.id,
            serverId = serverId,
            promptName = name,
            parameters = arguments
        )
    }
    
    
    private fun parseToolsFromServer(json: String, serverId: String): List<ToolInfo> {
        // Server returns tools as "id:description" separated by semicolons
        return json.split(";").mapNotNull { toolData ->
            val parts = toolData.split(":", limit = 2)
            if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null
            
            ToolInfo(
                name = parts[0].trim(),
                description = parts.getOrNull(1)?.trim() ?: "",
                serverId = serverId,
                serverName = serverId
            )
        }
    }
    
    private fun parseResourcesFromServer(json: String, serverId: String): List<ResourceInfo> {
        // Server returns resources as "scheme:name:description" separated by semicolons
        return json.split(";").mapNotNull { resourceData ->
            val parts = resourceData.split(":", limit = 3)
            if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null
            
            ResourceInfo(
                uri = "${parts[0].trim()}://resource", // Construct URI from scheme
                name = parts.getOrNull(1)?.trim() ?: "Resource",
                description = parts.getOrNull(2)?.trim() ?: "",
                serverId = serverId,
                serverName = serverId
            )
        }
    }
    
    private fun parsePromptsFromServer(json: String, serverId: String): List<PromptInfo> {
        // Server returns prompts as "id:description" separated by semicolons
        return json.split(";").mapNotNull { promptData ->
            val parts = promptData.split(":", limit = 2)
            if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null
            
            PromptInfo(
                name = parts[0].trim(),
                description = parts.getOrNull(1)?.trim() ?: "",
                serverId = serverId,
                serverName = serverId
            )
        }
    }
    
    private fun createToolsListResponse(tools: List<ToolInfo>): String {
        val toolsData = tools.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "serverId" to tool.serverId,
                "serverName" to tool.serverName,
                "inputSchema" to mapOf("type" to "object", "properties" to emptyMap<String, Any>())
            )
        }
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = UUID.randomUUID().toString(),
            result = mapOf("tools" to toolsData)
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createToolCallResponse(requestId: String, result: String): String {
        // Wrap the result string in standard MCP tool response format
        val resultData = mapOf("content" to listOf(mapOf("type" to "text", "text" to result)))
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = requestId,
            result = resultData
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createResourcesListResponse(resources: List<ResourceInfo>): String {
        val resourcesData = resources.map { resource ->
            mapOf(
                "uri" to resource.uri,
                "name" to resource.name,
                "description" to resource.description,
                "serverId" to resource.serverId,
                "serverName" to resource.serverName,
                "mimeType" to "text/plain"
            )
        }
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = UUID.randomUUID().toString(),
            result = mapOf("resources" to resourcesData)
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createResourceReadResponse(requestId: String, result: String): String {
        val resultData = mapOf(
            "contents" to listOf(
                mapOf(
                    "uri" to "example://resource",
                    "mimeType" to "text/plain",
                    "text" to result
                )
            )
        )
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = requestId,
            result = resultData
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createPromptsListResponse(prompts: List<PromptInfo>): String {
        val promptsData = prompts.map { prompt ->
            mapOf(
                "name" to prompt.name,
                "description" to prompt.description,
                "serverId" to prompt.serverId,
                "serverName" to prompt.serverName,
                "arguments" to listOf<Map<String, Any>>()
            )
        }
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = UUID.randomUUID().toString(),
            result = mapOf("prompts" to promptsData)
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createPromptGetResponse(requestId: String, result: String): String {
        val resultData = mapOf(
            "description" to "Generated prompt response",
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to mapOf(
                        "type" to "text",
                        "text" to result
                    )
                )
            )
        )
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = requestId,
            result = resultData
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createServersListResponse(servers: List<ServerStatusInfo>): String {
        val serversData = servers.map { server ->
            mapOf(
                "packageName" to server.packageName,
                "appName" to server.appName,
                "version" to server.version,
                "capabilities" to server.capabilities,
                "connectionStatus" to server.connectionStatus,
                "isConnected" to server.isConnected,
                "hasTools" to server.hasTools,
                "hasResources" to server.hasResources,
                "hasPrompts" to server.hasPrompts
            )
        }
        
        val response = jsonRpcSerializer.createSuccessResponse(
            requestId = UUID.randomUUID().toString(),
            result = mapOf("servers" to serversData)
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun createErrorResponse(code: Int, message: String, id: String? = null): String {
        val response = jsonRpcSerializer.createErrorResponse(
            requestId = id ?: "unknown",
            code = code,
            message = message
        )
        
        return jsonRpcSerializer.serialize(response)
    }
    
    private fun mapToJson(map: Map<String, Any>): String {
        // Simple JSON serialization for parameter passing
        val entries = map.entries.joinToString(",") { (key, value) ->
            when (value) {
                is String -> "\"$key\":\"$value\""
                is Number -> "\"$key\":$value"
                is Boolean -> "\"$key\":$value"
                else -> "\"$key\":\"$value\""
            }
        }
        return "{$entries}"
    }
}