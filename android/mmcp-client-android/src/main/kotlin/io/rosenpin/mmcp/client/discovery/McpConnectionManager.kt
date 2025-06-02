package io.rosenpin.mmcp.client.discovery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.rosenpin.mmcp.server.IMcpService
import io.rosenpin.mmcp.server.IMcpServiceCallback
import io.rosenpin.mmcp.server.IMcpToolService
import io.rosenpin.mmcp.server.IMcpResourceService
import io.rosenpin.mmcp.server.IMcpPromptService
import io.rosenpin.mmcp.server.McpConstants
import io.rosenpin.mmcp.server.McpException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages persistent connections to MCP servers and provides a unified API
 * for interacting with different service types (tools, resources, prompts).
 */
class McpConnectionManager(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private val _connections = MutableStateFlow<Map<String, ServerConnection>>(emptyMap())
    val connections: StateFlow<Map<String, ServerConnection>> = _connections.asStateFlow()
    
    private val activeConnections = mutableMapOf<String, ServerConnection>()
    
    companion object {
        private const val TAG = "McpConnectionManager"
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }
    
    /**
     * Represents an active connection to an MCP server with all its services
     */
    data class ServerConnection(
        val packageName: String,
        val mainService: IMcpService? = null,
        val toolService: IMcpToolService? = null,
        val resourceService: IMcpResourceService? = null,
        val promptService: IMcpPromptService? = null,
        val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
        val lastError: String? = null,
        val capabilities: List<String> = emptyList()
    ) {
        val isConnected: Boolean get() = status == ConnectionStatus.CONNECTED
        val hasTools: Boolean get() = toolService != null
        val hasResources: Boolean get() = resourceService != null
        val hasPrompts: Boolean get() = promptService != null
    }
    
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    /**
     * Connect to an MCP server and bind to all its available services
     */
    suspend fun connectToServer(packageName: String): Result<ServerConnection> {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Connecting to MCP server: $packageName")
                
                // Update status to connecting
                updateConnectionStatus(packageName, ConnectionStatus.CONNECTING)
                
                // Connect to main service first
                val mainService = connectToMainService(packageName)
                if (mainService == null) {
                    val error = "Failed to connect to main MCP service"
                    updateConnectionStatus(packageName, ConnectionStatus.ERROR, error)
                    return@withContext Result.failure(McpException(error))
                }
                
                // Get server capabilities
                val capabilities = try {
                    val capabilitiesJson = mainService.capabilities
                    parseCapabilities(capabilitiesJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get capabilities from $packageName", e)
                    emptyList()
                }
                
                // Connect to specialized services based on capabilities
                val toolService = if (capabilities.contains(McpConstants.Capabilities.TOOLS)) {
                    connectToToolService(packageName)
                } else null
                
                val resourceService = if (capabilities.contains(McpConstants.Capabilities.RESOURCES)) {
                    connectToResourceService(packageName)
                } else null
                
                val promptService = if (capabilities.contains(McpConstants.Capabilities.PROMPTS)) {
                    connectToPromptService(packageName)
                } else null
                
                val connection = ServerConnection(
                    packageName = packageName,
                    mainService = mainService,
                    toolService = toolService,
                    resourceService = resourceService,
                    promptService = promptService,
                    status = ConnectionStatus.CONNECTED,
                    capabilities = capabilities
                )
                
                activeConnections[packageName] = connection
                _connections.value = _connections.value + (packageName to connection)
                
                Log.d(TAG, "Successfully connected to $packageName with capabilities: $capabilities")
                Result.success(connection)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to server $packageName", e)
                updateConnectionStatus(packageName, ConnectionStatus.ERROR, e.message)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Connect to the main MCP service
     */
    private suspend fun connectToMainService(packageName: String): IMcpService? {
        return connectToService(packageName, McpConstants.ACTION_MCP_SERVICE) { binder ->
            IMcpService.Stub.asInterface(binder)
        }
    }
    
    /**
     * Connect to the tool service
     */
    private suspend fun connectToToolService(packageName: String): IMcpToolService? {
        return connectToService(packageName, McpConstants.ACTION_MCP_TOOL_SERVICE) { binder ->
            IMcpToolService.Stub.asInterface(binder)
        }
    }
    
    /**
     * Connect to the resource service
     */
    private suspend fun connectToResourceService(packageName: String): IMcpResourceService? {
        return connectToService(packageName, McpConstants.ACTION_MCP_RESOURCE_SERVICE) { binder ->
            IMcpResourceService.Stub.asInterface(binder)
        }
    }
    
    /**
     * Connect to the prompt service
     */
    private suspend fun connectToPromptService(packageName: String): IMcpPromptService? {
        return connectToService(packageName, McpConstants.ACTION_MCP_PROMPT_SERVICE) { binder ->
            IMcpPromptService.Stub.asInterface(binder)
        }
    }
    
    /**
     * Generic method to connect to any AIDL service
     */
    private suspend fun <T> connectToService(
        packageName: String,
        action: String,
        serviceFactory: (IBinder) -> T
    ): T? {
        val connectionDeferred = CompletableDeferred<T?>()
        
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val serviceInterface = service?.let(serviceFactory)
                    connectionDeferred.complete(serviceInterface)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating service interface for $action", e)
                    connectionDeferred.complete(null)
                }
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "Service disconnected: $name")
                // Note: We don't complete here as this indicates an unexpected disconnection
            }
        }
        
        return try {
            val intent = Intent(action).apply {
                setPackage(packageName)
            }
            
            val bound = context.bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE
            )
            
            if (!bound) {
                Log.w(TAG, "Failed to bind to $action in package $packageName")
                return null
            }
            
            // Wait for connection with timeout
            withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                connectionDeferred.await()
            }.also {
                if (it == null) {
                    Log.w(TAG, "Timeout connecting to $action in $packageName")
                    try {
                        context.unbindService(connection)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error unbinding service", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $action in $packageName", e)
            null
        }
    }
    
    /**
     * Parse capabilities JSON (simplified implementation)
     */
    private fun parseCapabilities(json: String): List<String> {
        // Server returns capabilities as comma-separated values
        return json.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    /**
     * Get an active connection to a server
     */
    fun getConnection(packageName: String): ServerConnection? {
        return activeConnections[packageName]
    }
    
    /**
     * Disconnect from a specific server
     */
    suspend fun disconnectFromServer(packageName: String): Result<Unit> {
        return withContext(Dispatchers.Main) {
            try {
                val connection = activeConnections[packageName]
                if (connection != null) {
                    // TODO: Properly unbind all services
                    activeConnections.remove(packageName)
                    _connections.value = _connections.value - packageName
                    Log.d(TAG, "Disconnected from $packageName")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from $packageName", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Update connection status
     */
    private fun updateConnectionStatus(
        packageName: String,
        status: ConnectionStatus,
        error: String? = null
    ) {
        val currentConnection = activeConnections[packageName] ?: ServerConnection(packageName)
        val updatedConnection = currentConnection.copy(
            status = status,
            lastError = error
        )
        
        activeConnections[packageName] = updatedConnection
        _connections.value = _connections.value + (packageName to updatedConnection)
    }
    
    /**
     * Get all connected servers
     */
    fun getConnectedServers(): List<ServerConnection> {
        return activeConnections.values.filter { it.isConnected }
    }
    
    /**
     * Check if connected to a specific server
     */
    fun isConnectedTo(packageName: String): Boolean {
        return activeConnections[packageName]?.isConnected == true
    }
    
    /**
     * Clean up all connections
     */
    fun cleanup() {
        activeConnections.clear()
        _connections.value = emptyMap()
        scope.cancel()
    }
}