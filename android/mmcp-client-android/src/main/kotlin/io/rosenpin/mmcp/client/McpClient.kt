package io.rosenpin.mmcp.client

import android.content.Context
import android.util.Log
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery
import io.rosenpin.mmcp.client.http.MCPHttpServer
import io.rosenpin.mmcp.server.McpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Main client API for discovering and interacting with MCP servers.
 * 
 * This is the primary entry point for LLM applications to:
 * - Discover available MCP servers on the device
 * - Connect to servers and access their capabilities  
 * - Execute tools, access resources, and use prompts
 * - Manage server connections and lifecycle
 * 
 * Usage:
 * ```kotlin
 * val client = McpClient(context)
 * client.startDiscovery()
 * 
 * // Observe discovered servers
 * client.discoveredServers.collect { servers ->
 *     servers.forEach { server -> 
 *         println("Found: ${server.serverInfo?.serverName}")
 *     }
 * }
 * 
 * // Connect to a server
 * val connection = client.connectToServer("com.example.mcpserver")
 * ```
 */
class McpClient(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private val discovery = McpServerDiscovery(context)
    private val connectionManager = McpConnectionManager(context)
    private var httpServer: MCPHttpServer? = null
    
    companion object {
        private const val TAG = "McpClient"
    }
    
    // Discovery API
    
    /**
     * Observable list of discovered MCP servers
     */
    val discoveredServers: StateFlow<List<McpServerDiscovery.DiscoveredServer>> 
        get() = discovery.discoveredServers
    
    /**
     * Whether discovery is currently running
     */
    val isScanning: StateFlow<Boolean> 
        get() = discovery.isScanning
    
    /**
     * Start discovering MCP servers on the device
     */
    suspend fun startDiscovery(): Result<Unit> {
        Log.d(TAG, "Starting MCP client discovery")
        return discovery.startDiscovery()
    }
    
    /**
     * Refresh the list of discovered servers
     */
    suspend fun refreshDiscovery(): Result<Unit> {
        return discovery.refresh()
    }
    
    /**
     * Get a specific discovered server by package name
     */
    fun getDiscoveredServer(packageName: String): McpServerDiscovery.DiscoveredServer? {
        return discovery.getServer(packageName)
    }
    
    // Connection Management API
    
    /**
     * Observable map of active server connections
     */
    val activeConnections: StateFlow<Map<String, McpConnectionManager.ServerConnection>>
        get() = connectionManager.connections
    
    /**
     * Connect to an MCP server and bind to all its services
     */
    suspend fun connectToServer(packageName: String): Result<McpConnectionManager.ServerConnection> {
        Log.d(TAG, "Connecting to MCP server: $packageName")
        
        // Verify server was discovered first
        val discoveredServer = getDiscoveredServer(packageName)
        if (discoveredServer == null) {
            val error = "Server $packageName not found. Run discovery first."
            Log.w(TAG, error)
            return Result.failure(McpException(error))
        }
        
        return connectionManager.connectToServer(packageName)
    }
    
    /**
     * Disconnect from a specific server
     */
    suspend fun disconnectFromServer(packageName: String): Result<Unit> {
        Log.d(TAG, "Disconnecting from MCP server: $packageName")
        return connectionManager.disconnectFromServer(packageName)
    }
    
    /**
     * Get an active connection to a server
     */
    fun getConnection(packageName: String): McpConnectionManager.ServerConnection? {
        return connectionManager.getConnection(packageName)
    }
    
    /**
     * Get all currently connected servers
     */
    fun getConnectedServers(): List<McpConnectionManager.ServerConnection> {
        return connectionManager.getConnectedServers()
    }
    
    /**
     * Check if connected to a specific server
     */
    fun isConnectedTo(packageName: String): Boolean {
        return connectionManager.isConnectedTo(packageName)
    }
    
    // Tool Execution API
    
    /**
     * Execute a tool on a connected server
     */
    suspend fun executeTool(
        packageName: String,
        toolName: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val toolService = connection.toolService
            ?: return Result.failure(McpException("Server $packageName does not support tools"))
        
        return try {
            Log.d(TAG, "Executing tool '$toolName' on $packageName")
            val parametersJson = mapToJson(parameters) // TODO: Implement proper JSON conversion
            val result = toolService.executeTool(toolName, parametersJson, null)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool '$toolName' on $packageName", e)
            Result.failure(e)
        }
    }
    
    /**
     * List available tools from a connected server
     */
    suspend fun listTools(packageName: String): Result<List<String>> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val toolService = connection.toolService
            ?: return Result.failure(McpException("Server $packageName does not support tools"))
        
        return try {
            val toolsJson = toolService.listTools()
            val tools = parseToolList(toolsJson) // TODO: Implement proper JSON parsing
            Result.success(tools)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing tools from $packageName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get information about a specific tool
     */
    suspend fun getToolInfo(packageName: String, toolId: String): Result<String> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val toolService = connection.toolService
            ?: return Result.failure(McpException("Server $packageName does not support tools"))
        
        return try {
            val info = toolService.getToolInfo(toolId)
            if (info.isNullOrEmpty()) {
                Result.failure(McpException("Tool '$toolId' not found"))
            } else {
                Result.success(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting tool info for '$toolId' from $packageName", e)
            Result.failure(e)
        }
    }
    
    // Resource Access API
    
    /**
     * Read a resource from a connected server
     */
    suspend fun readResource(
        packageName: String,
        resourceUri: String
    ): Result<String> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val resourceService = connection.resourceService
            ?: return Result.failure(McpException("Server $packageName does not support resources"))
        
        return try {
            Log.d(TAG, "Reading resource '$resourceUri' from $packageName")
            val result = resourceService.readResource(resourceUri, null)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading resource '$resourceUri' from $packageName", e)
            Result.failure(e)
        }
    }
    
    /**
     * List available resources from a connected server
     */
    suspend fun listResources(packageName: String): Result<List<String>> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val resourceService = connection.resourceService
            ?: return Result.failure(McpException("Server $packageName does not support resources"))
        
        return try {
            val resourcesJson = resourceService.listResources()
            val resources = parseResourceList(resourcesJson) // TODO: Implement proper JSON parsing
            Result.success(resources)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing resources from $packageName", e)
            Result.failure(e)
        }
    }
    
    // Prompt API
    
    /**
     * Get a prompt from a connected server
     */
    suspend fun getPrompt(
        packageName: String,
        promptName: String,
        parameters: Map<String, Any> = emptyMap()
    ): Result<String> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val promptService = connection.promptService
            ?: return Result.failure(McpException("Server $packageName does not support prompts"))
        
        return try {
            Log.d(TAG, "Getting prompt '$promptName' from $packageName")
            val parametersJson = mapToJson(parameters)
            val result = promptService.getPrompt(promptName, parametersJson, null)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting prompt '$promptName' from $packageName", e)
            Result.failure(e)
        }
    }
    
    /**
     * List available prompts from a connected server
     */
    suspend fun listPrompts(packageName: String): Result<List<String>> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val promptService = connection.promptService
            ?: return Result.failure(McpException("Server $packageName does not support prompts"))
        
        return try {
            val promptsJson = promptService.listPrompts()
            val prompts = parsePromptList(promptsJson) // TODO: Implement proper JSON parsing
            Result.success(prompts)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing prompts from $packageName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get information about a specific prompt
     */
    suspend fun getPromptInfo(packageName: String, promptId: String): Result<String> {
        val connection = getConnection(packageName)
            ?: return Result.failure(McpException("Not connected to server $packageName"))
        
        val promptService = connection.promptService
            ?: return Result.failure(McpException("Server $packageName does not support prompts"))
        
        return try {
            val info = promptService.getPromptInfo(promptId)
            if (info.isNullOrEmpty()) {
                Result.failure(McpException("Prompt '$promptId' not found"))
            } else {
                Result.success(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting prompt info for '$promptId' from $packageName", e)
            Result.failure(e)
        }
    }
    
    // HTTP Server API
    
    /**
     * Start the HTTP server for LLM integration
     */
    suspend fun startHttpServer(port: Int = MCPHttpServer.DEFAULT_PORT): Result<Unit> {
        return try {
            if (httpServer?.isRunning() == true) {
                Log.w(TAG, "HTTP server already running")
                return Result.success(Unit)
            }
            
            httpServer = MCPHttpServer(port, discovery, connectionManager)
            val result = httpServer!!.startServer()
            
            if (result.isSuccess) {
                Log.i(TAG, "MCP HTTP server started on port $port")
                // Auto-start discovery when HTTP server starts
                startDiscovery()
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop the HTTP server
     */
    fun stopHttpServer() {
        httpServer?.stopServer()
        httpServer = null
        Log.i(TAG, "MCP HTTP server stopped")
    }
    
    /**
     * Check if HTTP server is running
     */
    fun isHttpServerRunning(): Boolean = httpServer?.isRunning() == true
    
    /**
     * Get HTTP server port
     */
    fun getHttpServerPort(): Int? = httpServer?.getPort()
    
    // Lifecycle
    
    /**
     * Initialize the client (call this when your app starts)
     */
    fun initialize() {
        Log.d(TAG, "Initializing MCP client")
        
        // Auto-discover servers on initialization
        scope.launch {
            startDiscovery()
        }
    }
    
    /**
     * Clean up resources (call this when your app shuts down)
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up MCP client")
        stopHttpServer()
        discovery.cleanup()
        connectionManager.cleanup()
        scope.cancel()
    }
    
    // Helper methods (TODO: Implement proper JSON handling)
    
    private fun mapToJson(map: Map<String, Any>): String {
        // Simple JSON serialization for parameter passing
        if (map.isEmpty()) return "{}"
        
        val entries = map.entries.joinToString(",") { (key, value) ->
            when (value) {
                is String -> "\"$key\":\"$value\""
                is Number -> "\"$key\":$value"
                is Boolean -> "\"$key\":$value"
                null -> "\"$key\":null"
                else -> "\"$key\":\"$value\""
            }
        }
        return "{$entries}"
    }
    
    private fun parseToolList(response: String): List<String> {
        // Server returns format: "toolId1:description1;toolId2:description2"
        if (response.isEmpty() || response.startsWith("Error:") || response == "Service not initialized") {
            return emptyList()
        }
        
        return response.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 2) {
                // Extract the tool ID from the first part
                parts[0].trim()
            } else null
        }
    }
    
    private fun parseResourceList(response: String): List<String> {
        // Server returns format: "scheme:name:description;scheme2:name2:description2"
        if (response.isEmpty() || response.startsWith("Error:") || response == "Service not initialized") {
            return emptyList()
        }
        
        // Return the schemes that this server handles
        return response.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 3) {
                // Return just the scheme - the actual URI will be provided when reading
                parts[0].trim()
            } else null
        }
    }
    
    private fun parsePromptList(response: String): List<String> {
        // Server returns format: "promptId1:description1;promptId2:description2"
        if (response.isEmpty() || response.startsWith("Error:") || response == "Service not initialized") {
            return emptyList()
        }
        
        return response.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 2) {
                // Extract the prompt ID from the first part
                parts[0].trim()
            } else null
        }
    }
}