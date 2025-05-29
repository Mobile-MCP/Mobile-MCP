package io.rosenpin.mcp.server;

import io.rosenpin.mcp.server.IMcpServiceCallback;

/**
 * MCP Prompt Service interface for accessing prompt templates
 * This interface provides methods for listing and getting prompt templates from the MCP server
 */
interface IMcpPromptService {
    
    /**
     * List all available prompt templates provided by this server
     * @return JSON string containing array of prompt definitions
     */
    String listPrompts();
    
    /**
     * Get detailed information about a specific prompt template
     * @param promptName The name of the prompt template
     * @return JSON string with prompt details, or null if prompt not found
     */
    String getPromptInfo(String promptName);
    
    /**
     * Get a prompt template with optional parameters filled in
     * @param promptName The name of the prompt template
     * @param parameters JSON string containing prompt parameters (optional)
     * @param callback Callback for async prompt generation
     * @return JSON string with prompt content or request ID for async generation
     */
    String getPrompt(String promptName, String parameters, IMcpServiceCallback callback);
    
    /**
     * Check if a specific prompt template is available
     * @param promptName The name of the prompt to check
     * @return true if the prompt is available, false otherwise
     */
    boolean isPromptAvailable(String promptName);
    
    /**
     * Search for prompt templates matching the given query
     * @param query Search query string
     * @return JSON string containing array of matching prompts
     */
    String searchPrompts(String query);
}