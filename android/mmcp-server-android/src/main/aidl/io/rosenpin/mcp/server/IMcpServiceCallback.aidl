package io.rosenpin.mcp.server;

/**
 * Callback interface for receiving asynchronous responses and notifications from MCP servers
 * This interface allows the MCP server to send responses and notifications back to the client
 */
interface IMcpServiceCallback {
    
    /**
     * Called when a response is received for a previous request
     * @param requestId The ID of the original request
     * @param response JSON-RPC response string
     */
    void onResponse(String requestId, String response);
    
    /**
     * Called when an error occurs during request processing
     * @param requestId The ID of the original request (may be null for general errors)
     * @param error JSON-RPC error response string
     */
    void onError(String requestId, String error);
    
    /**
     * Called when the server sends a notification (not tied to a specific request)
     * @param notification JSON-RPC notification string
     */
    void onNotification(String notification);
    
    /**
     * Called when the server connection status changes
     * @param connected true if connected, false if disconnected
     * @param reason Optional reason for status change
     */
    void onConnectionStatusChanged(boolean connected, String reason);
}