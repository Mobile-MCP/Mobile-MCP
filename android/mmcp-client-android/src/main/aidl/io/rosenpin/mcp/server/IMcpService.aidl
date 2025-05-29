package io.rosenpin.mcp.server;

import io.rosenpin.mcp.server.IMcpServiceCallback;

/**
 * Main MCP Service interface for discovery and basic communication
 * This interface enables LLM apps to discover and communicate with MCP servers
 */
interface IMcpService {
    
    /**
     * Get the MCP server capabilities and metadata
     * @return JSON string containing server capabilities
     */
    String getCapabilities();
    
    /**
     * Initialize connection with the MCP server
     * @param clientInfo JSON string with client information
     * @param callback Callback for async responses
     * @return JSON string with initialization response
     */
    String initialize(String clientInfo, IMcpServiceCallback callback);
    
    /**
     * Send a ping to check if the server is alive
     * @return JSON string with pong response
     */
    String ping();
    
    /**
     * Get server information (name, version, etc.)
     * @return JSON string with server info
     */
    String getServerInfo();
    
    /**
     * Check if the server supports a specific capability
     * @param capability The capability to check (e.g., "tools", "resources")
     * @return true if supported, false otherwise
     */
    boolean supportsCapability(String capability);
    
    /**
     * Register a callback for notifications from the server
     * @param callback The callback to register
     */
    void registerCallback(IMcpServiceCallback callback);
    
    /**
     * Unregister a previously registered callback
     * @param callback The callback to unregister
     */
    void unregisterCallback(IMcpServiceCallback callback);
    
    /**
     * Send a raw JSON-RPC request to the server
     * @param request JSON-RPC request string
     * @return JSON-RPC response string
     */
    String sendRequest(String request);
}