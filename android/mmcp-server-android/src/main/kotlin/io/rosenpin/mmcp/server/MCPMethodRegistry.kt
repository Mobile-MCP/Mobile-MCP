package io.rosenpin.mmcp.server

import android.util.Log
import io.rosenpin.mmcp.server.annotations.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.net.URI
import kotlin.coroutines.Continuation
import kotlin.reflect.jvm.kotlinFunction

/**
 * Registry and execution engine for MCP annotated methods.
 * 
 * This class routes AIDL service calls to the appropriate annotated methods,
 * handles parameter conversion, manages async execution, and formats responses
 * according to MCP protocol specifications.
 */
class MCPMethodRegistry(
    private val serverInfo: MCPServerInfo,
    private val serverInstance: Any
) {
    
    companion object {
        private const val TAG = "MCPMethodRegistry"
    }
    
    // Keep track of async executions for cancellation/status
    private val runningExecutions = mutableMapOf<String, Job>()
    
    /**
     * Execute a tool method with the given parameters.
     * 
     * @param toolName Name of the tool to execute
     * @param parametersJson JSON string containing tool parameters (optional)
     * @param callback Callback for async results (optional)
     * @param scope Coroutine scope for async execution
     * @return JSON string with result or execution ID for async operations
     */
    fun executeTool(
        toolName: String,
        parametersJson: String?,
        callback: IMcpServiceCallback?,
        scope: CoroutineScope
    ): String {
        val tool = serverInfo.tools.find { it.name == toolName }
            ?: return createErrorResponse("Tool '$toolName' not found")
        
        return try {
            // Parse parameters
            val parameters = parseParameters(parametersJson)
            
            // Validate parameters against schema if provided
            if (tool.parametersSchema.isNotBlank() && tool.parametersSchema != "{}") {
                val validationErrors = validateParameters(parameters, tool.parametersSchema)
                if (validationErrors.isNotEmpty()) {
                    return createErrorResponse("Parameter validation failed: ${validationErrors.joinToString(", ")}")
                }
            }
            
            // Check if this is a suspend function by looking for Continuation parameter
            if (isSuspendFunction(tool.method)) {
                // Async execution
                val executionId = generateExecutionId()
                val job = scope.launch {
                    try {
                        val result = tool.invoke(serverInstance, parameters)
                        val response = formatToolResult(result)
                        
                        // Send result via callback if available
                        callback?.let { cb ->
                            try {
                                cb.onResponse(executionId, response)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send callback result", e)
                            }
                        }
                        
                        Log.d(TAG, "Tool '$toolName' completed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Tool '$toolName' execution failed", e)
                        
                        // Send error via callback if available
                        callback?.let { cb ->
                            try {
                                cb.onError(executionId, "Tool execution failed: ${e.message}")
                            } catch (callbackError: Exception) {
                                Log.w(TAG, "Failed to send callback error", callbackError)
                            }
                        }
                    } finally {
                        runningExecutions.remove(executionId)
                    }
                }
                
                runningExecutions[executionId] = job
                
                // Return execution ID for async tracking
                JSONObject(mapOf(
                    "executionId" to executionId,
                    "status" to "running",
                    "async" to true
                )).toString()
                
            } else {
                // Synchronous execution  
                val result = tool.invoke(serverInstance, parameters)
                formatToolResult(result)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool '$toolName'", e)
            createErrorResponse("Tool execution failed: ${e.message}")
        }
    }
    
    /**
     * Read a resource using the appropriate handler method.
     * 
     * @param resourceUri URI of the resource to read
     * @param callback Callback for async results (optional)
     * @param scope Coroutine scope for async execution
     * @return JSON string with resource content
     */
    fun readResource(
        resourceUri: String,
        callback: IMcpServiceCallback?,
        scope: CoroutineScope
    ): String {
        return try {
            val uri = URI(resourceUri)
            val scheme = uri.scheme ?: return createErrorResponse("Invalid URI: missing scheme")
            
            val resourceHandler = serverInfo.resources.find { it.scheme == scheme }
                ?: return createErrorResponse("No resource handler for scheme '$scheme'")
            
            // For resources, pass the full URI as a parameter
            val parameters = mapOf("uri" to resourceUri)
            
            // Check if this is a suspend function by looking for Continuation parameter
            if (isSuspendFunction(resourceHandler.method)) {
                // Async execution
                val executionId = generateExecutionId()
                val job = scope.launch {
                    try {
                        val result = invokeResourceMethod(resourceHandler, serverInstance, parameters)
                        val response = formatResourceResult(result, resourceUri)
                        
                        // Send result via callback if available
                        callback?.let { cb ->
                            try {
                                cb.onResponse(executionId, response)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send callback result", e)
                            }
                        }
                        
                        Log.d(TAG, "Resource '$resourceUri' read successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Resource '$resourceUri' read failed", e)
                        
                        // Send error via callback if available
                        callback?.let { cb ->
                            try {
                                cb.onError(executionId, "Resource read failed: ${e.message}")
                            } catch (callbackError: Exception) {
                                Log.w(TAG, "Failed to send callback error", callbackError)
                            }
                        }
                    } finally {
                        runningExecutions.remove(executionId)
                    }
                }
                
                runningExecutions[executionId] = job
                
                // Return execution ID for async tracking
                JSONObject(mapOf(
                    "executionId" to executionId,
                    "status" to "running",
                    "async" to true
                )).toString()
                
            } else {
                // Synchronous execution
                val result = invokeResourceMethod(resourceHandler, serverInstance, parameters)
                formatResourceResult(result, resourceUri)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading resource '$resourceUri'", e)
            createErrorResponse("Resource read failed: ${e.message}")
        }
    }
    
    /**
     * Generate a prompt using the appropriate handler method.
     * 
     * @param promptName Name of the prompt to generate
     * @param parametersJson JSON string containing prompt parameters (optional)
     * @param callback Callback for async results (optional) 
     * @param scope Coroutine scope for async execution
     * @return JSON string with generated prompt
     */
    fun getPrompt(
        promptName: String,
        parametersJson: String?,
        callback: IMcpServiceCallback?,
        scope: CoroutineScope
    ): String {
        val prompt = serverInfo.prompts.find { it.name == promptName }
            ?: return createErrorResponse("Prompt '$promptName' not found")
        
        return try {
            // Parse parameters
            val parameters = parseParameters(parametersJson)
            
            // Validate parameters against schema if provided
            if (prompt.parametersSchema.isNotBlank() && prompt.parametersSchema != "{}") {
                val validationErrors = validateParameters(parameters, prompt.parametersSchema)
                if (validationErrors.isNotEmpty()) {
                    return createErrorResponse("Parameter validation failed: ${validationErrors.joinToString(", ")}")
                }
            }
            
            // Check if this is a suspend function by looking for Continuation parameter
            if (isSuspendFunction(prompt.method)) {
                // Async execution
                val executionId = generateExecutionId()
                val job = scope.launch {
                    try {
                        val result = prompt.invoke(serverInstance, parameters)
                        val response = formatPromptResult(result, promptName)
                        
                        // Send result via callback if available
                        callback?.let { cb ->
                            try {
                                cb.onResponse(executionId, response)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send callback result", e)
                            }
                        }
                        
                        Log.d(TAG, "Prompt '$promptName' generated successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Prompt '$promptName' generation failed", e)
                        
                        // Send error via callback if available
                        callback?.let { cb ->
                            try {
                                cb.onError(executionId, "Prompt generation failed: ${e.message}")
                            } catch (callbackError: Exception) {
                                Log.w(TAG, "Failed to send callback error", callbackError)
                            }
                        }
                    } finally {
                        runningExecutions.remove(executionId)
                    }
                }
                
                runningExecutions[executionId] = job
                
                // Return execution ID for async tracking
                JSONObject(mapOf(
                    "executionId" to executionId,
                    "status" to "running",
                    "async" to true
                )).toString()
                
            } else {
                // Synchronous execution
                val result = prompt.invoke(serverInstance, parameters)
                formatPromptResult(result, promptName)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating prompt '$promptName'", e)
            createErrorResponse("Prompt generation failed: ${e.message}")
        }
    }
    
    // ===================================================================================
    // Parameter Handling and Validation
    // ===================================================================================
    
    private fun parseParameters(parametersJson: String?): Map<String, Any> {
        if (parametersJson.isNullOrBlank()) return emptyMap()
        
        return try {
            val jsonObject = JSONObject(parametersJson)
            jsonObject.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse parameters JSON: $parametersJson", e)
            emptyMap()
        }
    }
    
    private fun validateParameters(parameters: Map<String, Any>, schema: String): List<String> {
        // Basic parameter validation against JSON schema
        // For production use, consider using a proper JSON Schema validation library
        val errors = mutableListOf<String>()
        
        try {
            val schemaObj = JSONObject(schema)
            val required = schemaObj.optJSONArray("required")
            val properties = schemaObj.optJSONObject("properties")
            
            // Check required parameters
            if (required != null) {
                for (i in 0 until required.length()) {
                    val requiredParam = required.getString(i)
                    if (!parameters.containsKey(requiredParam)) {
                        errors.add("Missing required parameter: $requiredParam")
                    }
                }
            }
            
            // Basic type checking for provided parameters
            if (properties != null) {
                parameters.forEach { (paramName, value) ->
                    val propSchema = properties.optJSONObject(paramName)
                    if (propSchema != null) {
                        val expectedType = propSchema.optString("type")
                        if (!isValidParameterType(value, expectedType)) {
                            errors.add("Parameter '$paramName' has invalid type. Expected: $expectedType, got: ${value::class.simpleName}")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error validating parameters against schema", e)
            // Don't fail validation if schema parsing fails
        }
        
        return errors
    }
    
    private fun isValidParameterType(value: Any, expectedType: String): Boolean {
        return when (expectedType) {
            "string" -> value is String
            "number" -> value is Number
            "integer" -> value is Int || value is Long
            "boolean" -> value is Boolean
            "array" -> value is List<*> || value is Array<*> || value is JSONArray
            "object" -> value is Map<*, *> || value is JSONObject
            else -> true // Unknown type, allow it
        }
    }
    
    // ===================================================================================
    // Method Invocation
    // ===================================================================================
    
    private fun invokeResourceMethod(
        resourceInfo: ResourceMethodInfo,
        instance: Any,
        parameters: Map<String, Any>
    ): Any? {
        val method = resourceInfo.method
        val args = Array<Any?>(method.parameterCount) { null }
        
        // Map URI parameter - resource methods typically expect URI as first parameter
        if (method.parameterCount > 0) {
            args[0] = parameters["uri"]
        }
        
        return method.invoke(instance, *args)
    }
    
    // ===================================================================================
    // Result Formatting
    // ===================================================================================
    
    private fun formatToolResult(result: Any?): String {
        // ARCHITECTURAL FIX: Server library should return raw objects, not JSON
        // TODO: Remove this method entirely when client library handles result formatting
        // This formatting belongs in the client library, not server library
        return when (result) {
            null -> ""
            is String -> result
            is Number -> result.toString()
            is Boolean -> result.toString()
            is List<*> -> result.joinToString(", ")
            is Map<*, *> -> result.toString()
            else -> result.toString()
        }
    }
    
    private fun formatResourceResult(result: Any?, uri: String): String {
        // ARCHITECTURAL FIX: Server library should return raw objects, not JSON
        // TODO: Remove this method entirely when client library handles resource formatting
        // Resource formatting belongs in the client library
        return when (result) {
            is String -> result
            is ByteArray -> result.toString() // TODO: Client library should handle proper encoding
            else -> result?.toString() ?: ""
        }
    }
    
    private fun formatPromptResult(result: Any?, promptName: String): String {
        // ARCHITECTURAL FIX: Server library should return raw objects, not JSON
        // TODO: Remove this method entirely when client library handles prompt formatting
        // Prompt formatting belongs in the client library
        return when (result) {
            is String -> result
            else -> result?.toString() ?: ""
        }
    }
    
    // ===================================================================================
    // Utility Methods
    // ===================================================================================
    
    private fun isSuspendFunction(method: Method): Boolean {
        // Suspend functions have a Continuation parameter as the last parameter
        return method.parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"
    }
    
    private fun generateExecutionId(): String {
        return "exec_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
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
            val value = this.get(key)
            map[key] = when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value
            }
        }
        return map
    }
    
    /**
     * Extension function to convert JSONArray to List  
     */
    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            val value = this.get(i)
            list.add(when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value
            })
        }
        return list
    }
}