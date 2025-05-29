package io.rosenpin.mcp.server;

/**
 * MCP Discovery Service interface for finding available MCP servers
 * This interface allows MCP clients to discover servers installed on the device
 */
interface IMcpDiscoveryService {
    
    /**
     * Get a list of all available MCP servers on the device
     * @return JSON string containing array of server information
     */
    String discoverServers();
    
    /**
     * Get detailed information about a specific MCP server
     * @param serverPackage The package name of the server
     * @return JSON string with server details, or null if server not found
     */
    String getServerDetails(String serverPackage);
    
    /**
     * Check if a specific MCP server is available and running
     * @param serverPackage The package name of the server to check
     * @return true if the server is available, false otherwise
     */
    boolean isServerAvailable(String serverPackage);
    
    /**
     * Get the service connection info for a specific MCP server
     * @param serverPackage The package name of the server
     * @return JSON string with connection details (service class, action, etc.)
     */
    String getServerConnectionInfo(String serverPackage);
    
    /**
     * Register a new MCP server with the discovery service
     * This is typically called by MCP servers when they start up
     * @param serverInfo JSON string containing server registration information
     * @return true if registration was successful, false otherwise
     */
    boolean registerServer(String serverInfo);
    
    /**
     * Unregister an MCP server from the discovery service
     * This is typically called by MCP servers when they shut down
     * @param serverPackage The package name of the server to unregister
     * @return true if unregistration was successful, false otherwise
     */
    boolean unregisterServer(String serverPackage);
}