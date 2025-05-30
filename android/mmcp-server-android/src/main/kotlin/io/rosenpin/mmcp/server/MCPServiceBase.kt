package io.rosenpin.mmcp.server

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import io.rosenpin.mmcp.mmcpcore.protocol.JsonRpcSerializer
import io.rosenpin.mmcp.mmcpcore.protocol.McpProtocolCore
import io.rosenpin.mmcp.server.annotations.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException

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
    private var serverInfo: MCPServerInfo? = null
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
            serverInfo = discoveredServerInfo
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
        Log.i(TAG, "MCP Service destroyed: ${serverInfo?.name ?: "Unknown"}")
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
                val info = serverInfo ?: return createErrorResponse("Service not initialized")
                
                val capabilities: Map<String, Any> = mapOf(
                    "protocolVersion" to McpConstants.MCP_PROTOCOL_VERSION,
                    "serverInfo" to mapOf(
                        "name" to info.name,
                        "version" to info.version,
                        "description" to info.description
                    ),
                    "capabilities" to buildCapabilitiesObject()
                )
                
                JSONObject(capabilities).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting capabilities", e)
                createErrorResponse("Failed to get capabilities: ${e.message}")
            }
        }
        
        override fun initialize(clientInfo: String?, callback: IMcpServiceCallback?): String {
            return try {
                val info = serverInfo ?: return createErrorResponse("Service not initialized")
                
                // Parse client info if provided
                val clientData = if (clientInfo != null) {
                    try { JSONObject(clientInfo) } catch (e: Exception) { null }
                } else null
                
                Log.i(TAG, "Client initializing: ${clientData?.optString("name", "Unknown")}")
                
                // Return initialization response
                val response: Map<String, Any> = mapOf(
                    "protocolVersion" to McpConstants.MCP_PROTOCOL_VERSION,
                    "capabilities" to buildCapabilitiesObject(),
                    "serverInfo" to mapOf(
                        "name" to info.name,
                        "version" to info.version,
                        "description" to info.description
                    )
                )
                
                JSONObject(response).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                createErrorResponse("Initialization failed: ${e.message}")
            }
        }
        
        override fun ping(): String {
            return """{"status": "pong", "timestamp": ${System.currentTimeMillis()}}"""
        }
        
        override fun getServerInfo(): String {
            return try {
                val server = serverInfo ?: return createErrorResponse("Service not initialized")
                
                val info: Map<String, Any> = mapOf(
                    "id" to server.id,
                    "name" to server.name,
                    "description" to server.description,
                    "version" to server.version,
                    "capabilities" to server.capabilities
                )
                JSONObject(info).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting server info", e)
                createErrorResponse("Failed to get server info: ${e.message}")
            }
        }
        
        override fun supportsCapability(capability: String?): Boolean {
            return capability != null && serverInfo?.capabilities?.contains(capability) == true
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
                val info = serverInfo ?: return createErrorResponse("Service not initialized")
                
                val tools = info.tools.map { tool ->
                    mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "inputSchema" to if (tool.parametersSchema.isNotBlank()) {
                            JSONObject(tool.parametersSchema).toMap()
                        } else {
                            mapOf("type" to "object", "properties" to emptyMap<String, Any>())
                        }
                    )
                }
                
                JSONObject(mapOf("tools" to tools)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing tools", e)
                createErrorResponse("Failed to list tools: ${e.message}")
            }
        }
        
        override fun getToolInfo(toolName: String?): String? {
            return try {
                if (toolName == null) return null
                val server = serverInfo ?: return null
                
                val tool = annotationProcessor.findTool(server, toolName) ?: return null
                
                val info = mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "inputSchema" to if (tool.parametersSchema.isNotBlank()) {
                        JSONObject(tool.parametersSchema).toMap()
                    } else {
                        mapOf("type" to "object", "properties" to emptyMap<String, Any>())
                    }
                )
                
                JSONObject(info).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting tool info", e)
                null
            }
        }
        
        override fun executeTool(toolName: String?, parameters: String?, callback: IMcpServiceCallback?): String {
            return try {
                if (toolName == null) {
                    return createErrorResponse("Tool name cannot be null")
                }
                val registry = methodRegistry ?: return createErrorResponse("Service not initialized")
                
                registry.executeTool(toolName, parameters, callback, serviceScope)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool", e)
                createErrorResponse("Tool execution failed: ${e.message}")
            }
        }
        
        override fun isToolAvailable(toolName: String?): Boolean {
            val server = serverInfo ?: return false
            return toolName != null && annotationProcessor.findTool(server, toolName) != null
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
                val info = serverInfo ?: return createErrorResponse("Service not initialized")
                
                val resources = info.resources.map { resource ->
                    mapOf(
                        "uri" to "${resource.scheme}://example",
                        "name" to resource.name,
                        "description" to resource.description,
                        "mimeType" to resource.mimeType
                    )
                }
                
                JSONObject(mapOf("resources" to resources)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing resources", e)
                createErrorResponse("Failed to list resources: ${e.message}")
            }
        }
        
        override fun getResourceInfo(resourceUri: String?): String? {
            return try {
                if (resourceUri == null) return null
                
                val scheme = resourceUri.substringBefore("://")
                val resource = annotationProcessor.findResourcesForScheme(serverInfo, scheme).firstOrNull()
                    ?: return null
                
                val info = mapOf(
                    "uri" to resourceUri,
                    "name" to resource.name, 
                    "description" to resource.description,
                    "mimeType" to resource.mimeType
                )
                
                JSONObject(info).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting resource info", e)
                null
            }
        }
        
        override fun readResource(resourceUri: String?, callback: IMcpServiceCallback?): String {
            return try {
                if (resourceUri == null) {
                    return createErrorResponse("Resource URI cannot be null")
                }
                
                methodRegistry.readResource(resourceUri, callback, serviceScope)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading resource", e)
                createErrorResponse("Resource read failed: ${e.message}")
            }
        }
        
        override fun isResourceAvailable(resourceUri: String?): Boolean {
            if (resourceUri == null) return false
            val scheme = resourceUri.substringBefore("://")
            return annotationProcessor.findResourcesForScheme(serverInfo, scheme).isNotEmpty()
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
                val prompts = serverInfo.prompts.map { prompt ->
                    mapOf(
                        "name" to prompt.name,
                        "description" to prompt.description,
                        "arguments" to if (prompt.parametersSchema.isNotBlank()) {
                            val schema = JSONObject(prompt.parametersSchema)
                            // Convert JSON schema to MCP prompt arguments format
                            val properties = schema.optJSONObject("properties") ?: JSONObject()
                            properties.keys().asSequence().map { key ->
                                val prop = properties.getJSONObject(key)
                                mapOf(
                                    "name" to key,
                                    "description" to prop.optString("description", ""),
                                    "required" to schema.optJSONArray("required")?.toString()?.contains(key) ?: false
                                )
                            }.toList()
                        } else {
                            emptyList<Map<String, Any>>()
                        }
                    )
                }
                
                JSONObject(mapOf("prompts" to prompts)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing prompts", e)
                createErrorResponse("Failed to list prompts: ${e.message}")
            }
        }
        
        override fun getPromptInfo(promptName: String?): String? {
            return try {
                if (promptName == null) return null
                
                val prompt = annotationProcessor.findPrompt(serverInfo, promptName) ?: return null
                
                val info = mapOf(
                    "name" to prompt.name,
                    "description" to prompt.description,
                    "arguments" to if (prompt.parametersSchema.isNotBlank()) {
                        val schema = JSONObject(prompt.parametersSchema)
                        val properties = schema.optJSONObject("properties") ?: JSONObject()
                        properties.keys().asSequence().map { key ->
                            val prop = properties.getJSONObject(key)
                            mapOf(
                                "name" to key,
                                "description" to prop.optString("description", ""),
                                "required" to schema.optJSONArray("required")?.toString()?.contains(key) ?: false
                            )
                        }.toList()
                    } else {
                        emptyList<Map<String, Any>>()
                    }
                )
                
                JSONObject(info).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prompt info", e)
                null
            }
        }
        
        override fun getPrompt(promptName: String?, parameters: String?, callback: IMcpServiceCallback?): String {
            return try {
                if (promptName == null) {
                    return createErrorResponse("Prompt name cannot be null")
                }
                
                methodRegistry.getPrompt(promptName, parameters, callback, serviceScope)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prompt", e)
                createErrorResponse("Prompt generation failed: ${e.message}")
            }
        }
        
        override fun isPromptAvailable(promptName: String?): Boolean {
            return promptName != null && annotationProcessor.findPrompt(serverInfo, promptName) != null
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
        
        if (serverInfo.capabilities.contains(McpConstants.Capabilities.TOOLS)) {
            caps["tools"] = mapOf("listChanged" to true)
        }
        
        if (serverInfo.capabilities.contains(McpConstants.Capabilities.RESOURCES)) {
            caps["resources"] = mapOf(
                "subscribe" to true,
                "listChanged" to true
            )
        }
        
        if (serverInfo.capabilities.contains(McpConstants.Capabilities.PROMPTS)) {
            caps["prompts"] = mapOf("listChanged" to true)
        }
        
        return caps
    }
    
    private fun createErrorResponse(message: String): String {
        return JSONObject(mapOf(
            "error" to mapOf(
                "code" to -32603,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
        )).toString()
    }
    
    /**
     * Extension function to convert JSONObject to Map
     */
    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = this.get(key)
        }
        return map
    }
}