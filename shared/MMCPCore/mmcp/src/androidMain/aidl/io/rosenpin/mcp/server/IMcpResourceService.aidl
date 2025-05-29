package io.rosenpin.mcp.server;

import io.rosenpin.mcp.server.IMcpServiceCallback;

/**
 * MCP Resource Service interface for accessing resources
 * This interface provides methods for listing and reading resources provided by the MCP server
 */
interface IMcpResourceService {
    
    /**
     * List all available resources provided by this server
     * @return JSON string containing array of resource definitions
     */
    String listResources();
    
    /**
     * Get detailed information about a specific resource
     * @param resourceUri The URI of the resource
     * @return JSON string with resource details, or null if resource not found
     */
    String getResourceInfo(String resourceUri);
    
    /**
     * Read the content of a resource
     * @param resourceUri The URI of the resource to read
     * @param callback Callback for async read results
     * @return JSON string with resource content or request ID for async reading
     */
    String readResource(String resourceUri, IMcpServiceCallback callback);
    
    /**
     * Check if a specific resource is available
     * @param resourceUri The URI of the resource to check
     * @return true if the resource is available, false otherwise
     */
    boolean isResourceAvailable(String resourceUri);
    
    /**
     * Subscribe to updates for a specific resource
     * @param resourceUri The URI of the resource to subscribe to
     * @param callback Callback for receiving updates
     * @return true if subscription was successful, false otherwise
     */
    boolean subscribeToResource(String resourceUri, IMcpServiceCallback callback);
    
    /**
     * Unsubscribe from updates for a specific resource
     * @param resourceUri The URI of the resource to unsubscribe from
     * @return true if unsubscription was successful, false otherwise
     */
    boolean unsubscribeFromResource(String resourceUri);
    
    /**
     * Search for resources matching the given query
     * @param query Search query string
     * @return JSON string containing array of matching resources
     */
    String searchResources(String query);
}