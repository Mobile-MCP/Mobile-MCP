package io.rosenpin.mmcp.client.direct

import kotlinx.coroutines.flow.Flow

/**
 * Direct API for in-process MCP tool calling that bypasses HTTP for MLKit-style LLMs.
 * 
 * This interface provides direct access to MCP functionality using Kotlin coroutines
 * for async operations, eliminating HTTP overhead for in-process communication.
 */
interface MCPDirectApi {
    
    /**
     * List all available tools from all connected MCP servers
     * @return List of available tools with their metadata
     */
    suspend fun listTools(): List<MCPTool>
    
    /**
     * Execute a tool call on a specific server
     * @param serverId The package name of the MCP server
     * @param toolName The name of the tool to execute
     * @param parameters Tool parameters as a map
     * @return Tool execution result
     * @throws MCPException if tool execution fails
     */
    suspend fun callTool(serverId: String, toolName: String, parameters: Map<String, Any>): Any?
    
    /**
     * List all available resources from all connected MCP servers
     * @return List of available resources with their metadata
     */
    suspend fun listResources(): List<MCPResource>
    
    /**
     * Read a resource from a specific server
     * @param serverId The package name of the MCP server
     * @param uri The URI of the resource to read
     * @return Resource content as bytes
     * @throws MCPException if resource read fails
     */
    suspend fun readResource(serverId: String, uri: String): ByteArray
    
    /**
     * List all available prompts from all connected MCP servers
     * @return List of available prompts with their metadata
     */
    suspend fun listPrompts(): List<MCPPrompt>
    
    /**
     * Get a prompt from a specific server
     * @param serverId The package name of the MCP server
     * @param promptName The name of the prompt to get
     * @param parameters Prompt parameters as a map
     * @return Prompt execution result
     * @throws MCPException if prompt get fails
     */
    suspend fun getPrompt(serverId: String, promptName: String, parameters: Map<String, Any>): MCPPromptResult
    
    /**
     * Get information about all discovered servers
     * @return List of server status information
     */
    suspend fun listServers(): List<MCPServerInfo>
    
    /**
     * Observe changes in server connectivity status
     * @return Flow that emits updates when server connections change
     */
    fun observeServerStatus(): Flow<List<MCPServerInfo>>
    
    /**
     * Observe changes in available tools across all servers
     * @return Flow that emits updates when tool availability changes
     */
    fun observeTools(): Flow<List<MCPTool>>
}