package io.rosenpin.mmcp.server

/**
 * Constants used throughout the MCP framework for Android
 * These define service actions, capabilities, and other shared values
 */
object McpConstants {
    
    // Service Actions for AIDL binding
    const val ACTION_MCP_SERVICE = "io.rosenpin.mmcp.action.MCP_SERVICE"
    const val ACTION_MCP_TOOL_SERVICE = "io.rosenpin.mmcp.action.MCP_TOOL_SERVICE"
    const val ACTION_MCP_RESOURCE_SERVICE = "io.rosenpin.mmcp.action.MCP_RESOURCE_SERVICE"
    const val ACTION_MCP_PROMPT_SERVICE = "io.rosenpin.mmcp.action.MCP_PROMPT_SERVICE"
    const val ACTION_MCP_DISCOVERY_SERVICE = "io.rosenpin.mmcp.action.MCP_DISCOVERY_SERVICE"
    
    // MCP Protocol Version
    const val MCP_PROTOCOL_VERSION = "2024-11-05"
    
    // MCP Capabilities
    object Capabilities {
        const val TOOLS = "tools"
        const val RESOURCES = "resources"
        const val PROMPTS = "prompts"
        const val LOGGING = "logging"
        const val SAMPLING = "sampling"
        const val ROOTS = "roots"
    }
    
    // Service Categories for discovery
    object ServiceCategory {
        const val MAIN_SERVICE = "main"
        const val TOOL_SERVICE = "tools"
        const val RESOURCE_SERVICE = "resources"
        const val PROMPT_SERVICE = "prompts"
        const val DISCOVERY_SERVICE = "discovery"
    }
    
    // Intent extras for service communication
    object Extras {
        const val SERVER_PACKAGE = "server_package"
        const val SERVER_NAME = "server_name"
        const val SERVER_VERSION = "server_version"
        const val CAPABILITIES = "capabilities"
        const val PROTOCOL_VERSION = "protocol_version"
    }
    
    // Permission for MCP service access
    const val PERMISSION_MCP_SERVICE = "io.rosenpin.mmcp.permission.ACCESS_MCP_SERVICE"
    
    // Timeouts and limits
    const val DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L
    const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
    const val MAX_CONCURRENT_REQUESTS = 50
}