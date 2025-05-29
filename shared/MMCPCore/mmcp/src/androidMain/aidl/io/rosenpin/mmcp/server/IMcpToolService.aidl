package io.rosenpin.mmcp.server;

import io.rosenpin.mmcp.server.IMcpServiceCallback;

/**
 * MCP Tool Service interface for executing tools
 * This interface provides methods for listing and executing tools provided by the MCP server
 */
interface IMcpToolService {
    
    /**
     * List all available tools provided by this server
     * @return JSON string containing array of tool definitions
     */
    String listTools();
    
    /**
     * Get detailed information about a specific tool
     * @param toolName The name of the tool
     * @return JSON string with tool details, or null if tool not found
     */
    String getToolInfo(String toolName);
    
    /**
     * Execute a tool with the given parameters
     * @param toolName The name of the tool to execute
     * @param parameters JSON string containing tool parameters
     * @param callback Callback for async execution results
     * @return JSON string with execution result or request ID for async execution
     */
    String executeTool(String toolName, String parameters, IMcpServiceCallback callback);
    
    /**
     * Check if a specific tool is available
     * @param toolName The name of the tool to check
     * @return true if the tool is available, false otherwise
     */
    boolean isToolAvailable(String toolName);
    
    /**
     * Cancel a running tool execution
     * @param executionId The ID of the execution to cancel
     * @return true if cancellation was successful, false otherwise
     */
    boolean cancelExecution(String executionId);
    
    /**
     * Get the status of a running tool execution
     * @param executionId The ID of the execution to check
     * @return JSON string with execution status
     */
    String getExecutionStatus(String executionId);
}