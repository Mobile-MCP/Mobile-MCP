package io.rosenpin.mmcp.client.direct

/**
 * Represents a tool available from an MCP server
 */
data class MCPTool(
    val name: String,
    val description: String,
    val serverId: String,
    val serverName: String,
    val inputSchema: MCPSchema
)

/**
 * Represents a parameter for an MCP tool
 */
data class MCPParameter(
    val name: String,
    val type: String,
    val description: String?,
    val required: Boolean = false,
    val enum: List<String>? = null,
    val default: Any? = null
)

/**
 * Represents a JSON schema for tool input/output
 */
data class MCPSchema(
    val type: String,
    val properties: Map<String, MCPParameter> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean = false
)

/**
 * Represents a resource available from an MCP server
 */
data class MCPResource(
    val uri: String,
    val name: String,
    val description: String?,
    val mimeType: String?,
    val serverId: String,
    val serverName: String
)

/**
 * Represents a prompt available from an MCP server
 */
data class MCPPrompt(
    val name: String,
    val description: String?,
    val serverId: String,
    val serverName: String,
    val arguments: List<MCPPromptArgument> = emptyList()
)

/**
 * Represents an argument for an MCP prompt
 */
data class MCPPromptArgument(
    val name: String,
    val description: String?,
    val required: Boolean = false
)

/**
 * Represents the result of a prompt execution
 */
data class MCPPromptResult(
    val description: String?,
    val messages: List<MCPMessage>
)

/**
 * Represents a message in a prompt result
 */
data class MCPMessage(
    val role: String,
    val content: MCPContent
)

/**
 * Represents content in a message
 */
sealed class MCPContent {
    data class Text(val text: String) : MCPContent()
    data class Image(val data: String, val mimeType: String) : MCPContent()
    data class Resource(val uri: String, val mimeType: String?) : MCPContent()
}

/**
 * Represents information about an MCP server
 */
data class MCPServerInfo(
    val packageName: String,
    val appName: String,
    val version: String,
    val capabilities: List<String>,
    val connectionStatus: MCPConnectionStatus,
    val isConnected: Boolean,
    val hasTools: Boolean,
    val hasResources: Boolean,
    val hasPrompts: Boolean,
    val protocolVersion: String = "2024-11-05"
)

/**
 * Connection status enumeration
 */
enum class MCPConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
    TIMEOUT
}

/**
 * Custom exception for MCP-related errors
 */
class MCPException(
    message: String,
    val code: Int = -32603,
    val serverId: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    companion object {
        // JSON-RPC error codes
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
        
        // MCP-specific error codes
        const val SERVER_NOT_CONNECTED = -32000
        const val TOOL_NOT_FOUND = -32001
        const val RESOURCE_NOT_FOUND = -32002
        const val PROMPT_NOT_FOUND = -32003
        const val EXECUTION_FAILED = -32004
        const val TIMEOUT_ERROR = -32005
    }
}