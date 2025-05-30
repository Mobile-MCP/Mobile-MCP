package io.rosenpin.mmcp.client.discovery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import io.rosenpin.mmcp.server.IMcpService
import io.rosenpin.mmcp.server.McpConstants
import io.rosenpin.mmcp.server.McpServerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel

/**
 * Core service discovery engine for finding MCP servers on the device.
 * 
 * This class handles:
 * - Scanning installed apps for MCP service declarations
 * - Binding to AIDL services and querying capabilities  
 * - Managing service connections and metadata
 * - Providing a reactive API for discovered servers
 */
class McpServerDiscovery(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val packageManager = context.packageManager
    
    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val activeConnections = mutableMapOf<String, ServiceConnection>()
    
    companion object {
        private const val TAG = "McpServerDiscovery"
        private const val CONNECTION_TIMEOUT_MS = 10_000L
    }
    
    /**
     * Represents a discovered MCP server with its metadata and connection status
     */
    data class DiscoveredServer(
        val packageName: String,
        val serviceName: String,
        val serverInfo: McpServerInfo? = null,
        val capabilities: List<String> = emptyList(),
        val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
        val lastError: String? = null
    )
    
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING, 
        CONNECTED,
        ERROR
    }
    
    /**
     * Start scanning for MCP servers on the device
     */
    suspend fun startDiscovery(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isScanning.value = true
            Log.d(TAG, "Starting MCP server discovery...")
            
            val discoveredServers = scanForMcpServers()
            Log.d(TAG, "Found ${discoveredServers.size} potential MCP servers")
            
            // Query each server for detailed information
            val serversWithDetails = discoveredServers.map { server ->
                scope.async {
                    queryServerDetails(server)
                }
            }.awaitAll()
            
            _discoveredServers.value = serversWithDetails.filterNotNull()
            Log.d(TAG, "Successfully queried ${_discoveredServers.value.size} MCP servers")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during server discovery", e)
            Result.failure(e)
        } finally {
            _isScanning.value = false
        }
    }
    
    /**
     * Scan the PackageManager for apps declaring MCP services
     */
    private suspend fun scanForMcpServers(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<DiscoveredServer>()
        
        // Scan for each type of MCP service
        val serviceActions = listOf(
            McpConstants.ACTION_MCP_SERVICE,
            McpConstants.ACTION_MCP_TOOL_SERVICE,
            McpConstants.ACTION_MCP_RESOURCE_SERVICE,
            McpConstants.ACTION_MCP_PROMPT_SERVICE,
            McpConstants.ACTION_MCP_DISCOVERY_SERVICE
        )
        
        serviceActions.forEach { action ->
            val intent = Intent(action)
            val resolveInfos = packageManager.queryIntentServices(
                intent,
                PackageManager.GET_META_DATA or PackageManager.GET_RESOLVED_FILTER
            )
            
            resolveInfos.forEach { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo
                if (serviceInfo != null && serviceInfo.exported) {
                    val server = DiscoveredServer(
                        packageName = serviceInfo.packageName,
                        serviceName = serviceInfo.name
                    )
                    
                    // Avoid duplicates from the same package
                    if (servers.none { it.packageName == server.packageName }) {
                        servers.add(server)
                        Log.d(TAG, "Found MCP service: ${serviceInfo.packageName}/${serviceInfo.name}")
                    }
                }
            }
        }
        
        servers
    }
    
    /**
     * Connect to a discovered server and query its details
     */
    private suspend fun queryServerDetails(server: DiscoveredServer): DiscoveredServer? {
        return withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            try {
                Log.d(TAG, "Querying server details for ${server.packageName}")
                
                val serviceInfo = connectToServer(server.packageName)
                if (serviceInfo != null) {
                    server.copy(
                        serverInfo = serviceInfo,
                        capabilities = serviceInfo.capabilities,
                        connectionStatus = ConnectionStatus.CONNECTED
                    )
                } else {
                    server.copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        lastError = "Failed to connect to server"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying server ${server.packageName}", e)
                server.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    lastError = e.message
                )
            }
        }
    }
    
    /**
     * Connect to an MCP server and retrieve its information
     */
    private suspend fun connectToServer(packageName: String): McpServerInfo? {
        val connectionDeferred = CompletableDeferred<IMcpService?>()
        
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val mcpService = IMcpService.Stub.asInterface(service)
                connectionDeferred.complete(mcpService)
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                connectionDeferred.complete(null)
                activeConnections.remove(packageName)
            }
        }
        
        return withContext(Dispatchers.Main) {
            try {
                val intent = Intent(McpConstants.ACTION_MCP_SERVICE).apply {
                    setPackage(packageName)
                }
                
                val bound = context.bindService(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE
                )
                
                if (!bound) {
                    Log.w(TAG, "Failed to bind to service in package $packageName")
                    return@withContext null
                }
                
                activeConnections[packageName] = connection
                
                // Wait for connection with timeout
                val service = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                    connectionDeferred.await()
                }
                
                if (service != null) {
                    // Query server information
                    val serverInfoJson = service.serverInfo
                    // Parse JSON to McpServerInfo (simplified for now)
                    val serverInfo = parseServerInfo(serverInfoJson, packageName)
                    
                    // Disconnect after querying
                    context.unbindService(connection)
                    activeConnections.remove(packageName)
                    
                    serverInfo
                } else {
                    context.unbindService(connection)
                    activeConnections.remove(packageName)
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to server $packageName", e)
                activeConnections[packageName]?.let { conn ->
                    context.unbindService(conn)
                    activeConnections.remove(packageName)
                }
                null
            }
        }
    }
    
    /**
     * Parse server info JSON (simplified implementation)
     */
    private fun parseServerInfo(json: String, packageName: String): McpServerInfo {
        // TODO: Implement proper JSON parsing
        // For now, create a basic server info
        return McpServerInfo(
            packageName = packageName,
            serviceName = "McpService", // Default
            serverName = "MCP Server", // Default  
            version = "1.0.0", // Default
            description = "MCP Server from $packageName",
            capabilities = listOf("tools", "resources") // Default
        )
    }
    
    /**
     * Get a specific discovered server by package name
     */
    fun getServer(packageName: String): DiscoveredServer? {
        return _discoveredServers.value.find { it.packageName == packageName }
    }
    
    /**
     * Refresh discovery by re-scanning all servers
     */
    suspend fun refresh(): Result<Unit> {
        return startDiscovery()
    }
    
    /**
     * Clean up all active connections
     */
    fun cleanup() {
        activeConnections.values.forEach { connection ->
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding service", e)
            }
        }
        activeConnections.clear()
        scope.cancel()
    }
}