package io.rosenpin.mmcp.server

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcSerializer
import io.rosenpin.mmcp.mmcpcore.protocol.McpProtocolCore
import io.rosenpin.mmcp.server.annotations.*
import io.rosenpin.mmcp.server.annotations.ServerInfo
import kotlinx.coroutines.*

/**
 * Base service class for MCP servers on Android.
 * 
 * 3rd party developers extend this class and add @MCPServer, @MCPTool, @MCPResource, 
 * and @MCPPrompt annotations to their methods. This base class automatically:
 * 
 * - Discovers all annotated methods using reflection
 * - Implements all AIDL interfaces (IMcpService, IMcpToolService, etc.)
 * - Routes AIDL calls to the appropriate annotated methods
 * - Handles parameter conversion and validation
 * - Manages async execution and callbacks
 * 
 * Example usage:
 * ```kotlin
 * @MCPServer(id = "com.example.fileserver", name = "File Server")
 * class FileServerService : MCPServiceBase() {
 *     
 *     @MCPTool(id = "list_files", name = "List Files")
 *     suspend fun listFiles(@MCPParam("path") path: String): List<String> {
 *         return File(path).listFiles()?.map { it.name } ?: emptyList()
 *     }
 * }
 * ```
 */
abstract class MCPServiceBase : Service() {
    
    companion object {
        private const val TAG = "MCPServiceBase"
    }
    
    // Service discovery and method registry
    private var cachedServerInfo: ServerInfo? = null
    private var methodRegistry: MCPMethodRegistry? = null
    private val annotationProcessor = MCPAnnotationProcessor()
    
    // JSON-RPC and MCP protocol handling  
    private val jsonRpcSerializer = JsonRpcSerializer()
    private val protocolCore = McpProtocolCore()
    
    // Async execution
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Callback management
    private val callbacks = RemoteCallbackList<IMcpServiceCallback>()
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Discover annotated methods in the concrete implementation
            val discoveredServerInfo = annotationProcessor.processServerClass(this::class.java)
                ?: throw IllegalStateException("${this::class.java.simpleName} must be annotated with @MCPServer")
            
            // Validate the server configuration
            val validationErrors = annotationProcessor.validateServerClass(this::class.java)
            if (validationErrors.isNotEmpty()) {
                throw IllegalStateException("Server validation failed: ${validationErrors.joinToString(", ")}")
            }
            
            // Assign to instance variables
            cachedServerInfo = discoveredServerInfo
            methodRegistry = MCPMethodRegistry(discoveredServerInfo, this)
            
            Log.i(TAG, "MCP Service initialized: ${discoveredServerInfo.name} (${discoveredServerInfo.id})")
            Log.i(TAG, "Capabilities: ${discoveredServerInfo.capabilities.joinToString(", ")}")
            Log.i(TAG, "Tools: ${discoveredServerInfo.tools.size}, Resources: ${discoveredServerInfo.resources.size}, Prompts: ${discoveredServerInfo.prompts.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MCP service", e)
            throw e
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        callbacks.kill()
        Log.i(TAG, "MCP Service destroyed: ${cachedServerInfo?.name ?: "Unknown"}")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return when (intent?.action) {
            McpConstants.ACTION_MCP_SERVICE -> mainServiceBinder
            McpConstants.ACTION_MCP_TOOL_SERVICE -> toolServiceBinder
            McpConstants.ACTION_MCP_RESOURCE_SERVICE -> resourceServiceBinder
            McpConstants.ACTION_MCP_PROMPT_SERVICE -> promptServiceBinder
            else -> {
                Log.w(TAG, "Unknown service action: ${intent?.action}")
                null
            }
        }
    }
    
    // ===================================================================================
    // AIDL Service Implementation - Main Service
    // ===================================================================================
    
    private val mainServiceBinder = object : IMcpService.Stub() {
        
        override fun getCapabilities(): String {
            return try {
                val info = cachedServerInfo ?: return "Service not initialized"

                info.capabilities.joinToString(",")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting capabilities", e)
                "Error: ${e.message}"
            }
        }
        
        override fun initialize(clientInfo: String?, callback: IMcpServiceCallback?): String {
            return try {
                val info = cachedServerInfo ?: return "Service not initialized"

                Log.i(TAG, "Client initializing: ${clientInfo ?: "Unknown"}")

                "${info.name}|${info.version}|${info.description}"
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                "Error: ${e.message}"
            }
        }
        
        override fun ping(): String {
            // ARCHITECTURAL FIX: Return simple pong, not JSON
            return "pong"
        }
        
        override fun getServerInfo(): String {
            return try {
                val server = cachedServerInfo ?: return "Service not initialized"

                "${server.id}|${server.name}|${server.description}|${server.version}"
            } catch (e: Exception) {
                Log.e(TAG, "Error getting server info", e)
                "Error: ${e.message}"
            }
        }
        
        override fun supportsCapability(capability: String?): Boolean {
            return capability != null && cachedServerInfo?.capabilities?.contains(capability) == true
        }
        
        override fun registerCallback(callback: IMcpServiceCallback?) {
            if (callback != null) {
                callbacks.register(callback)
                Log.d(TAG, "Registered callback")
            }
        }
        
        override fun unregisterCallback(callback: IMcpServiceCallback?) {
            if (callback != null) {
                callbacks.unregister(callback)
                Log.d(TAG, "Unregistered callback")
            }
        }
        
        override fun sendRequest(request: String?): String {
            return if (request != null) {
                protocolCore.processMessage(request) ?: createErrorResponse("Invalid request")
            } else {
                createErrorResponse("Request cannot be null")
            }
        }
    }
    
    // ===================================================================================
    // AIDL Service Implementation - Tool Service  
    // ===================================================================================
    
    private val toolServiceBinder = object : IMcpToolService.Stub() {
        
        override fun listTools(): String {
            return try {
                val info = cachedServerInfo ?: return "Service not initialized"
                
                // ARCHITECTURAL FIX: Return simple tool list, not JSON
                info.tools.joinToString(";") { "${it.name}:${it.description}" }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing tools", e)
                "Error: ${e.message}"
            }
        }
        
        override fun getToolInfo(toolName: String?): String? {
            return try {
                if (toolName == null) return null
                val info = cachedServerInfo ?: return null
                
                val tool = annotationProcessor.findTool(info, toolName) ?: return null
                
                // ARCHITECTURAL FIX: Return simple tool info, not JSON
                "${tool.name}:${tool.description}:${tool.parametersSchema}"
            } catch (e: Exception) {
                Log.e(TAG, "Error getting tool info", e)
                null
            }
        }
        
        override fun executeTool(toolName: String?, parameters: String?, callback: IMcpServiceCallback?): String {
            return try {
                if (toolName == null) {
                    return "Error: Tool name cannot be null"
                }
                val registry = methodRegistry ?: return "Error: Service not initialized"
                
                registry.executeTool(toolName, parameters, callback, serviceScope)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool", e)
                "Error: Tool execution failed: ${e.message}"
            }
        }
        
        override fun isToolAvailable(toolName: String?): Boolean {
            val info = cachedServerInfo ?: return false
            return toolName != null && annotationProcessor.findTool(info, toolName) != null
        }
        
        override fun cancelExecution(executionId: String?): Boolean {
            // TODO: Implement execution cancellation
            return false
        }
        
        override fun getExecutionStatus(executionId: String?): String {
            // TODO: Implement execution status tracking
            return """{"status": "unknown", "executionId": "$executionId"}"""
        }
    }
    
    // ===================================================================================
    // AIDL Service Implementation - Resource Service
    // ===================================================================================
    
    private val resourceServiceBinder = object : IMcpResourceService.Stub() {
        
        override fun listResources(): String {
            return try {
                val info = cachedServerInfo ?: return "Service not initialized"
                
                // ARCHITECTURAL FIX: Return simple resource list, not JSON
                info.resources.joinToString(";") { "${it.scheme}:${it.name}:${it.description}" }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing resources", e)
                "Error: ${e.message}"
            }
        }
        
        override fun getResourceInfo(resourceUri: String?): String? {
            return try {
                if (resourceUri == null) return null
                val info = cachedServerInfo ?: return null
                
                val scheme = resourceUri.substringBefore("://")
                val resource = annotationProcessor.findResourcesForScheme(info, scheme).firstOrNull()
                    ?: return null
                
                // ARCHITECTURAL FIX: Return simple resource info, not JSON
                "${resourceUri}:${resource.name}:${resource.description}:${resource.mimeType}"
            } catch (e: Exception) {
                Log.e(TAG, "Error getting resource info", e)
                null
            }
        }
        
        override fun readResource(resourceUri: String?, callback: IMcpServiceCallback?): String {
            return try {
                if (resourceUri == null) {
                    return "Error: Resource URI cannot be null"
                }
                
                methodRegistry?.readResource(resourceUri, callback, serviceScope) ?: "Error: Service not initialized"
            } catch (e: Exception) {
                Log.e(TAG, "Error reading resource", e)
                "Error: Resource read failed: ${e.message}"
            }
        }
        
        override fun isResourceAvailable(resourceUri: String?): Boolean {
            if (resourceUri == null) return false
            val info = cachedServerInfo ?: return false
            val scheme = resourceUri.substringBefore("://")
            return annotationProcessor.findResourcesForScheme(info, scheme).isNotEmpty()
        }
        
        override fun subscribeToResource(resourceUri: String?, callback: IMcpServiceCallback?): Boolean {
            // TODO: Implement resource subscriptions
            return false
        }
        
        override fun unsubscribeFromResource(resourceUri: String?): Boolean {
            // TODO: Implement resource subscriptions  
            return false
        }
        
        override fun searchResources(query: String?): String {
            // TODO: Implement resource search
            return """{"resources": []}"""
        }
    }
    
    // ===================================================================================
    // AIDL Service Implementation - Prompt Service
    // ===================================================================================
    
    private val promptServiceBinder = object : IMcpPromptService.Stub() {
        
        override fun listPrompts(): String {
            return try {
                val info = cachedServerInfo ?: return "Service not initialized"
                
                // ARCHITECTURAL FIX: Return simple prompt list, not JSON
                info.prompts.joinToString(";") { "${it.name}:${it.description}" }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing prompts", e)
                "Error: ${e.message}"
            }
        }
        
        override fun getPromptInfo(promptName: String?): String? {
            return try {
                if (promptName == null) return null
                val info = cachedServerInfo ?: return null
                
                val prompt = annotationProcessor.findPrompt(info, promptName) ?: return null
                
                // ARCHITECTURAL FIX: Return simple prompt info, not JSON
                "${prompt.name}:${prompt.description}:${prompt.parametersSchema}"
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prompt info", e)
                null
            }
        }
        
        override fun getPrompt(promptName: String?, parameters: String?, callback: IMcpServiceCallback?): String {
            return try {
                if (promptName == null) {
                    return "Error: Prompt name cannot be null"
                }
                
                methodRegistry?.getPrompt(promptName, parameters, callback, serviceScope) ?: "Error: Service not initialized"
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prompt", e)
                "Error: Prompt generation failed: ${e.message}"
            }
        }
        
        override fun isPromptAvailable(promptName: String?): Boolean {
            val info = cachedServerInfo ?: return false
            return promptName != null && annotationProcessor.findPrompt(info, promptName) != null
        }
        
        override fun searchPrompts(query: String?): String {
            // TODO: Implement prompt search
            return """{"prompts": []}"""
        }
    }
    
    // ===================================================================================
    // Helper Methods
    // ===================================================================================
    
    private fun buildCapabilitiesObject(): Map<String, Any> {
        val caps = mutableMapOf<String, Any>()
        val info = cachedServerInfo ?: return caps
        
        if (info.capabilities.contains(McpConstants.Capabilities.TOOLS)) {
            caps["tools"] = mapOf("listChanged" to true)
        }
        
        if (info.capabilities.contains(McpConstants.Capabilities.RESOURCES)) {
            caps["resources"] = mapOf(
                "subscribe" to true,
                "listChanged" to true
            )
        }
        
        if (info.capabilities.contains(McpConstants.Capabilities.PROMPTS)) {
            caps["prompts"] = mapOf("listChanged" to true)
        }
        
        return caps
    }
    
    private fun createErrorResponse(message: String): String {
        // ARCHITECTURAL FIX: Return simple error, not JSON
        return "Error: $message"
    }
    
}