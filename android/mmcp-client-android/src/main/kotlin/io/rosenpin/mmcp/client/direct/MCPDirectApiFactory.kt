package io.rosenpin.mmcp.client.direct

import android.content.Context
import io.rosenpin.mmcp.client.discovery.McpConnectionManager
import io.rosenpin.mmcp.client.discovery.McpServerDiscovery

/**
 * Factory for creating MCPDirectApi instances with proper dependency injection.
 * 
 * This factory simplifies the creation of the direct API by managing the 
 * dependencies (discovery and connection manager) internally.
 */
object MCPDirectApiFactory {
    
    /**
     * Create a new MCPDirectApi instance with the given context.
     * 
     * @param context Android context for service binding and discovery
     * @return Configured MCPDirectApi instance ready for use
     */
    fun create(context: Context): MCPDirectApi {
        val discovery = McpServerDiscovery(context)
        val connectionManager = McpConnectionManager(context)
        
        return MCPDirectApiImpl(discovery, connectionManager)
    }
    
    /**
     * Create a new MCPDirectApi instance with custom dependencies.
     * 
     * This method is useful for testing or when you want to customize
     * the discovery or connection management behavior.
     * 
     * @param discovery Custom server discovery implementation
     * @param connectionManager Custom connection manager implementation
     * @return Configured MCPDirectApi instance
     */
    fun create(
        discovery: McpServerDiscovery,
        connectionManager: McpConnectionManager
    ): MCPDirectApi {
        return MCPDirectApiImpl(discovery, connectionManager)
    }
}