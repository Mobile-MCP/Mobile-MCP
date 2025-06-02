package io.rosenpin.mmcp.server.annotations

import java.lang.reflect.Method

/**
 * Converts a parameter value to match the target method parameter type.
 * Handles common type conversions for MCP method invocation.
 */
fun convertParameterType(value: Any?, targetType: Class<*>): Any? {
    // Handle null values for primitive types by providing defaults
    if (value == null) {
        return when (targetType) {
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Boolean::class.javaPrimitiveType -> false
            Byte::class.javaPrimitiveType -> 0.toByte()
            Short::class.javaPrimitiveType -> 0.toShort()
            Char::class.javaPrimitiveType -> '\u0000'
            else -> null // Non-primitives can be null
        }
    }
    
    return when {
        targetType == value::class.java -> value
        targetType == Double::class.java || targetType == Double::class.javaPrimitiveType -> {
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: 0.0
                else -> value
            }
        }
        targetType == Int::class.java || targetType == Int::class.javaPrimitiveType -> {
            when (value) {
                is Double -> value.toInt() // Gson parses all numbers as Double
                is Float -> value.toInt()
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> value
            }
        }
        targetType == Long::class.java || targetType == Long::class.javaPrimitiveType -> {
            when (value) {
                is Double -> value.toLong() // Gson parses all numbers as Double
                is Float -> value.toLong()
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: 0L
                else -> value
            }
        }
        targetType == Float::class.java || targetType == Float::class.javaPrimitiveType -> {
            when (value) {
                is Double -> value.toFloat() // Gson parses all numbers as Double
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull() ?: 0f
                else -> value
            }
        }
        targetType == Boolean::class.java || targetType == Boolean::class.javaPrimitiveType -> {
            when (value) {
                is Boolean -> value
                is String -> value.toBoolean()
                else -> false
            }
        }
        targetType == String::class.java -> value.toString()
        else -> value
    }
}

/**
 * Invokes a method with parameter mapping and type conversion.
 * Used by both tools and prompts to handle MCP parameter binding.
 */
fun invokeMethodWithParameterMapping(
    method: Method,
    instance: Any,
    arguments: Map<String, Any>?,
    parameterMapping: Map<String, Int>
): Any? {
    val args = Array<Any?>(method.parameterCount) { null }
    
    arguments?.forEach { (paramName, value) ->
        parameterMapping[paramName]?.let { index ->
            val paramType = method.parameterTypes[index]
            args[index] = convertParameterType(value, paramType)
        }
    }
    
    return method.invoke(instance, *args)
}

/**
 * Information about an annotated tool method discovered via reflection.
 * Contains all metadata needed to expose the method as an MCP tool.
 */
data class ToolMethodInfo(
    /**
     * Unique identifier for the tool.
     */
    val id: String,
    
    /**
     * Human-readable name of the tool.
     */
    val name: String,
    
    /**
     * Description of what the tool does.
     */
    val description: String,
    
    /**
     * JSON schema for tool parameters.
     */
    val parametersSchema: String,
    
    /**
     * The Java reflection Method object for this tool.
     */
    val method: Method,
    
    /**
     * Mapping from MCP parameter names to method parameter indices.
     * Used to correctly pass parameters when invoking the method.
     */
    val parameterMapping: Map<String, Int>
) {
    /**
     * Invoke this tool with MCP-compliant arguments object.
     * Follows MCP tools/call format: {name: string, arguments?: object}
     * 
     * @param instance The server instance to invoke the method on
     * @param arguments The arguments object from MCP tools/call request
     * @return The result of the tool invocation
     */
    fun invoke(instance: Any, arguments: Map<String, Any>?): Any? {
        return invokeMethodWithParameterMapping(method, instance, arguments, parameterMapping)
    }
}

/**
 * Information about an annotated resource method discovered via reflection.
 * Contains all metadata needed to expose the method as an MCP resource provider.
 */
data class ResourceMethodInfo(
    /**
     * URI scheme that this resource handler supports.
     */
    val scheme: String,
    
    /**
     * Human-readable name of the resource type.
     */
    val name: String,
    
    /**
     * Description of what resources this handler provides.
     */
    val description: String,
    
    /**
     * Default MIME type for resources.
     */
    val mimeType: String,
    
    /**
     * The Java reflection Method object for this resource handler.
     */
    val method: Method
)

/**
 * Information about an annotated prompt method discovered via reflection.
 * Contains all metadata needed to expose the method as an MCP prompt generator.
 */
data class PromptMethodInfo(
    /**
     * Unique identifier for the prompt.
     */
    val id: String,
    
    /**
     * Human-readable name of the prompt.
     */
    val name: String,
    
    /**
     * Description of what the prompt generates.
     */
    val description: String,
    
    /**
     * JSON schema for prompt parameters.
     */
    val parametersSchema: String,
    
    /**
     * The Java reflection Method object for this prompt generator.
     */
    val method: Method,
    
    /**
     * Mapping from MCP parameter names to method parameter indices.
     */
    val parameterMapping: Map<String, Int>
) {
    /**
     * Invoke this prompt with MCP-compliant arguments object.
     * Follows MCP prompts/get format: {name: string, arguments?: object}
     * 
     * @param instance The server instance to invoke the method on
     * @param arguments The arguments object from MCP prompts/get request
     * @return The prompt result
     */
    fun invoke(instance: Any, arguments: Map<String, Any>?): Any? {
        return invokeMethodWithParameterMapping(method, instance, arguments, parameterMapping)
    }
}

/**
 * Complete information about an MCP server discovered via annotation processing.
 * Contains the server metadata and all discovered capabilities.
 */
data class ServerInfo(
    /**
     * Unique server identifier.
     */
    val id: String,
    
    /**
     * Human-readable server name.
     */
    val name: String,
    
    /**
     * Server description.
     */
    val description: String,
    
    /**
     * Server version.
     */
    val version: String,
    
    /**
     * List of MCP capabilities this server supports.
     */
    val capabilities: List<String>,
    
    /**
     * All tool methods discovered in this server.
     */
    val tools: List<ToolMethodInfo> = emptyList(),
    
    /**
     * All resource methods discovered in this server.
     */
    val resources: List<ResourceMethodInfo> = emptyList(),
    
    /**
     * All prompt methods discovered in this server.
     */
    val prompts: List<PromptMethodInfo> = emptyList(),
    
    /**
     * The server class that was processed.
     */
    val serverClass: Class<*>
)