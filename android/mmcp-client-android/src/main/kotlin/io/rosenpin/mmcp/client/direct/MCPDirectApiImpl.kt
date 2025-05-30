package io.rosenpin.mmcp.client.direct

import android.util.Log
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.coroutines.coroutineContext

/**
 * Implementation of MCPDirectApi that provides direct access to MCP functionality
 * by routing requests to AIDL services without HTTP overhead.
 * 
 * This implementation uses the existing connection manager and discovery service
 * to bridge direct API calls to the underlying AIDL services.
 */
class MCPDirectApiImpl(
    private val discovery: McpServerDiscovery,
    private val connectionManager: McpConnectionManager
) : MCPDirectApi {
    
    companion object {
        private const val TAG = "MCPDirectApiImpl"
    }
    
    private val jsonRpcSerializer = JsonRpcSerializer()
    
    override suspend fun listTools(): List<MCPTool> {
        return withContext(Dispatchers.IO) {
            try {
                val connections = connectionManager.getConnectedServers()
                val toolLists = connections.mapNotNull { connection ->
                    if (connection.hasTools) {
                        async {
                            try {
                                val toolsJson = connection.toolService?.listTools() ?: "[]"
                                parseToolsFromServer(toolsJson, connection.packageName)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get tools from ${connection.packageName}", e)
                                emptyList<MCPTool>()
                            }
                        }
                    } else null
                }
                
                toolLists.awaitAll().flatten()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing tools", e)
                throw MCPException("Failed to list tools: ${e.message}", MCPException.INTERNAL_ERROR)
            }
        }
    }
    
    override suspend fun callTool(serverId: String, toolName: String, parameters: Map<String, Any>): Any? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = connectionManager.getConnection(serverId)
                    ?: throw MCPException(
                        "Server $serverId not connected", 
                        MCPException.SERVER_NOT_CONNECTED, 
                        serverId
                    )
                
                if (!connection.hasTools) {
                    throw MCPException(
                        "Server $serverId does not support tools",
                        MCPException.METHOD_NOT_FOUND,
                        serverId
                    )
                }
                
                val parametersJson = mapToJson(parameters)
                val result = connection.toolService?.executeTool(toolName, parametersJson, null)
                    ?: throw MCPException(
                        "Tool execution failed for $toolName",
                        MCPException.EXECUTION_FAILED,
                        serverId
                    )
                
                parseToolResult(result)
            } catch (e: MCPException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error calling tool $toolName on $serverId", e)
                throw MCPException(
                    "Tool call failed: ${e.message}",
                    MCPException.EXECUTION_FAILED,
                    serverId,
                    e
                )
            }
        }
    }
    
    override suspend fun listResources(): List<MCPResource> {
        return withContext(Dispatchers.IO) {
            try {
                val connections = connectionManager.getConnectedServers()
                val resourceLists = connections.mapNotNull { connection ->
                    if (connection.hasResources) {
                        async {
                            try {
                                val resourcesJson = connection.resourceService?.listResources() ?: "[]"
                                parseResourcesFromServer(resourcesJson, connection.packageName)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get resources from ${connection.packageName}", e)
                                emptyList<MCPResource>()
                            }
                        }
                    } else null
                }
                
                resourceLists.awaitAll().flatten()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing resources", e)
                throw MCPException("Failed to list resources: ${e.message}", MCPException.INTERNAL_ERROR)
            }
        }
    }
    
    override suspend fun readResource(serverId: String, uri: String): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                val connection = connectionManager.getConnection(serverId)
                    ?: throw MCPException(
                        "Server $serverId not connected",
                        MCPException.SERVER_NOT_CONNECTED,
                        serverId
                    )
                
                if (!connection.hasResources) {
                    throw MCPException(
                        "Server $serverId does not support resources",
                        MCPException.METHOD_NOT_FOUND,
                        serverId
                    )
                }
                
                val result = connection.resourceService?.readResource(uri, null)
                    ?: throw MCPException(
                        "Resource read failed for $uri",
                        MCPException.RESOURCE_NOT_FOUND,
                        serverId
                    )
                
                parseResourceContent(result)
            } catch (e: MCPException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error reading resource $uri from $serverId", e)
                throw MCPException(
                    "Resource read failed: ${e.message}",
                    MCPException.EXECUTION_FAILED,
                    serverId,
                    e
                )
            }
        }
    }
    
    override suspend fun listPrompts(): List<MCPPrompt> {
        return withContext(Dispatchers.IO) {
            try {
                val connections = connectionManager.getConnectedServers()
                val promptLists = connections.mapNotNull { connection ->
                    if (connection.hasPrompts) {
                        async {
                            try {
                                val promptsJson = connection.promptService?.listPrompts() ?: "[]"
                                parsePromptsFromServer(promptsJson, connection.packageName)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get prompts from ${connection.packageName}", e)
                                emptyList<MCPPrompt>()
                            }
                        }
                    } else null
                }
                
                promptLists.awaitAll().flatten()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing prompts", e)
                throw MCPException("Failed to list prompts: ${e.message}", MCPException.INTERNAL_ERROR)
            }
        }
    }
    
    override suspend fun getPrompt(serverId: String, promptName: String, parameters: Map<String, Any>): MCPPromptResult {
        return withContext(Dispatchers.IO) {
            try {
                val connection = connectionManager.getConnection(serverId)
                    ?: throw MCPException(
                        "Server $serverId not connected",
                        MCPException.SERVER_NOT_CONNECTED,
                        serverId
                    )
                
                if (!connection.hasPrompts) {
                    throw MCPException(
                        "Server $serverId does not support prompts",
                        MCPException.METHOD_NOT_FOUND,
                        serverId
                    )
                }
                
                val parametersJson = mapToJson(parameters)
                val result = connection.promptService?.getPrompt(promptName, parametersJson, null)
                    ?: throw MCPException(
                        "Prompt get failed for $promptName",
                        MCPException.PROMPT_NOT_FOUND,
                        serverId
                    )
                
                parsePromptResult(result)
            } catch (e: MCPException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prompt $promptName from $serverId", e)
                throw MCPException(
                    "Prompt get failed: ${e.message}",
                    MCPException.EXECUTION_FAILED,
                    serverId,
                    e
                )
            }
        }
    }
    
    override suspend fun listServers(): List<MCPServerInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val discoveredServers = discovery.discoveredServers.value
                val connections = connectionManager.connections.value
                
                discoveredServers.map { discovered ->
                    val connection = connections[discovered.packageName]
                    MCPServerInfo(
                        packageName = discovered.packageName,
                        appName = discovered.serverInfo?.serverName ?: "Unknown",
                        version = discovered.serverInfo?.protocolVersion ?: "1.0",
                        capabilities = discovered.capabilities,
                        connectionStatus = mapConnectionStatus(connection?.status),
                        isConnected = connection?.isConnected ?: false,
                        hasTools = connection?.hasTools ?: false,
                        hasResources = connection?.hasResources ?: false,
                        hasPrompts = connection?.hasPrompts ?: false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing servers", e)
                throw MCPException("Failed to list servers: ${e.message}", MCPException.INTERNAL_ERROR)
            }
        }
    }
    
    override fun observeServerStatus(): Flow<List<MCPServerInfo>> {
        return combine(
            discovery.discoveredServers,
            connectionManager.connections
        ) { discoveredServers, connections ->
            discoveredServers.map { discovered ->
                val connection = connections[discovered.packageName]
                MCPServerInfo(
                    packageName = discovered.packageName,
                    appName = discovered.serverInfo?.serverName ?: "Unknown",
                    version = discovered.serverInfo?.protocolVersion ?: "1.0",
                    capabilities = discovered.capabilities,
                    connectionStatus = mapConnectionStatus(connection?.status),
                    isConnected = connection?.isConnected ?: false,
                    hasTools = connection?.hasTools ?: false,
                    hasResources = connection?.hasResources ?: false,
                    hasPrompts = connection?.hasPrompts ?: false
                )
            }
        }
    }
    
    override fun observeTools(): Flow<List<MCPTool>> {
        return connectionManager.connections.map { connections ->
            try {
                connections.values.filter { it.hasTools && it.isConnected }
                    .mapNotNull { connection ->
                        try {
                            val toolsJson = connection.toolService?.listTools() ?: "[]"
                            parseToolsFromServer(toolsJson, connection.packageName)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get tools from ${connection.packageName}", e)
                            null
                        }
                    }.flatten()
            } catch (e: Exception) {
                Log.e(TAG, "Error observing tools", e)
                emptyList()
            }
        }
    }
    
    // Helper methods for parsing responses
    
    private fun parseToolsFromServer(json: String, serverId: String): List<MCPTool> {
        // TODO: Implement proper JSON parsing of tool schemas
        // For now, return mock data
        return listOf(
            MCPTool(
                name = "example_tool",
                description = "Example tool from $serverId",
                serverId = serverId,
                serverName = serverId,
                inputSchema = MCPSchema(
                    type = "object",
                    properties = mapOf(
                        "input" to MCPParameter("input", "string", "Input parameter", true)
                    ),
                    required = listOf("input")
                )
            )
        )
    }
    
    private fun parseResourcesFromServer(json: String, serverId: String): List<MCPResource> {
        // TODO: Implement proper JSON parsing
        return listOf(
            MCPResource(
                uri = "example://resource",
                name = "Example Resource",
                description = "Example resource from $serverId",
                mimeType = "text/plain",
                serverId = serverId,
                serverName = serverId
            )
        )
    }
    
    private fun parsePromptsFromServer(json: String, serverId: String): List<MCPPrompt> {
        // TODO: Implement proper JSON parsing
        return listOf(
            MCPPrompt(
                name = "example_prompt",
                description = "Example prompt from $serverId",
                serverId = serverId,
                serverName = serverId,
                arguments = listOf(
                    MCPPromptArgument("input", "Input for the prompt", true)
                )
            )
        )
    }
    
    private fun parseToolResult(result: String): Any? {
        // TODO: Implement proper JSON parsing
        return mapOf("result" to result)
    }
    
    private fun parseResourceContent(result: String): ByteArray {
        // TODO: Implement proper content parsing based on MCP protocol
        return result.toByteArray()
    }
    
    private fun parsePromptResult(result: String): MCPPromptResult {
        // TODO: Implement proper JSON parsing
        return MCPPromptResult(
            description = "Generated prompt",
            messages = listOf(
                MCPMessage(
                    role = "user",
                    content = MCPContent.Text(result)
                )
            )
        )
    }
    
    private fun mapConnectionStatus(status: McpConnectionManager.ConnectionStatus?): MCPConnectionStatus {
        return when (status) {
            McpConnectionManager.ConnectionStatus.DISCONNECTED -> MCPConnectionStatus.DISCONNECTED
            McpConnectionManager.ConnectionStatus.CONNECTING -> MCPConnectionStatus.CONNECTING
            McpConnectionManager.ConnectionStatus.CONNECTED -> MCPConnectionStatus.CONNECTED
            McpConnectionManager.ConnectionStatus.ERROR -> MCPConnectionStatus.FAILED
            null -> MCPConnectionStatus.DISCONNECTED
        }
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