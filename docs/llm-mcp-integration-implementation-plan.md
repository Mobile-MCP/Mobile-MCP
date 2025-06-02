# LLM-MCP Integration Implementation Plan

## Overview

This document outlines the detailed implementation plan for integrating a locally-running LLM with MCP support into our Mobile MCP Framework example app. The goal is to demonstrate how mobile LLMs can discover and use MCP servers from 3rd party apps using our existing infrastructure.

## Architecture Design

### System Architecture

```text
┌────────────────────────────────────────────────────────┐
│ Example App (LLM + MCP Demo)                           │
│ ┌────────────────────────────────────────────────────┐ │
│ │ UI Layer                                           │ │
│ │ • Chat Interface (Compose)                         │ │
│ │ • MCP Server Management Screen                     │ │
│ │ • Settings & Configuration                         │ │
│ └────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────┐ │
│ │ LLM Service Layer                                  │ │
│ │ • llama.cpp JNI Bindings                          │ │
│ │ • Model Management                                 │ │
│ │ • HTTP Tool Calling to localhost:11434            │ │
│ └────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────┐ │
│ │ MCP HTTP Server (mmcp-client-android)             │ │
│ │ • Running on port 11434                           │ │
│ │ • Exposes MCP endpoints:                          │ │
│ │   - GET /mcp/tools/list                           │ │
│ │   - POST /mcp/tools/call                          │ │
│ │   - GET /mcp/resources/list                       │ │
│ │   - POST /mcp/resources/read                      │ │
│ │   - GET /mcp/servers                              │ │
│ └────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────┐ │
│ │ MCP Client Core (mmcp-client-android)             │ │
│ │ • Server Discovery via AIDL                        │ │
│ │ • AIDL-to-HTTP Bridge                             │ │
│ │ • Permission Management                            │ │
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
                        │
                        │ AIDL IPC
                        │
┌────────────────────────────────────────────────────────┐
│ 3rd Party Apps with MCP Servers                        │
│ • PhoneMCPService (contacts, calls)                    │
│ • File Manager MCP                                     │
│ • Calendar MCP                                         │
│ • etc.                                                 │
└────────────────────────────────────────────────────────┘
```

### Component Breakdown

#### 1. LLM Service Component

**Purpose**: Manages the local LLM engine lifecycle and inference operations

**Key Classes**:
```kotlin
class LLMService : Service() {
    private lateinit var llamaEngine: LlamaEngine
    private lateinit var mcpHttpServer: MCPHttpServer
    private val httpClient = OkHttpClient()
    
    override fun onCreate() {
        // Start MCP HTTP server
        mcpHttpServer = MCPHttpServer(
            port = 11434,
            discovery = McpServerDiscovery(context),
            connectionManager = McpConnectionManager(context)
        )
        mcpHttpServer.startServer()
        
        // Initialize LLM with HTTP tool calling configuration
        llamaEngine = LlamaEngine(
            baseUrl = "http://localhost:11434"
        )
    }
}

class LlamaEngine {
    // JNI wrapper around llama.cpp
    // Model loading and management
    // Inference with streaming support
    // HTTP tool calling configuration
    
    external fun configureToolCalling(baseUrl: String)
    external fun generateWithTools(prompt: String, callback: (String) -> Unit)
}
```

#### 2. MCP HTTP Server (Already Implemented)

**Purpose**: Bridges HTTP requests from LLM to AIDL-based MCP servers

**Key Endpoints**:
- `GET /mcp/tools/list` - Returns all available tools from discovered servers
- `POST /mcp/tools/call` - Executes a tool with JSON-RPC request body:
  ```json
  {
    "jsonrpc": "2.0",
    "id": "123",
    "method": "tools/call",
    "params": {
      "serverId": "io.rosenpin.mcp.phonemcpserver",
      "name": "get_contacts",
      "arguments": {"limit": 10}
    }
  }
  ```
- `GET /mcp/servers` - Lists all discovered MCP servers and their status

#### 3. Chat UI Component

**Purpose**: Provides user interface for interacting with the LLM and managing MCP servers

**Key Screens**:
- **Chat Screen**: Message input, streaming responses, tool call visualization
- **MCP Servers Screen**: Discovery, connection status, permissions
- **Settings Screen**: Model selection, performance tuning

## Implementation Phases

### Phase 1: LLM Engine Integration

#### 1.1 NDK Setup and llama.cpp Integration

**Components to implement**:
- Android NDK configuration with CMake
- llama.cpp source integration as git submodule
- JNI bindings for core inference functions
- HTTP tool calling configuration

**Technical details**:
```kotlin
// Native methods declaration
external fun loadModel(modelPath: String): Long
external fun setToolCallEndpoint(endpoint: String)
external fun generateText(
    contextPtr: Long, 
    prompt: String, 
    maxTokens: Int,
    temperature: Float,
    toolsSystemPrompt: String,
    callback: (String) -> Unit
): String
external fun releaseModel(contextPtr: Long)
```

**Function Calling Configuration**:
```cpp
// In llama.cpp integration
void configureToolCalling(const char* toolsSchema) {
    // Configure llama.cpp with function calling chat template
    gpt_params params;
    params.use_mirostat = 2; // Better for structured output
    params.chat_template = "chatml"; // Or model-specific template
    params.grammar = "json"; // Enforce JSON output for tool calls
}
```

**Native llama.cpp Function Calling**:
```kotlin
// JNI wrapper
external fun setToolSchema(toolsJson: String)
external fun enableFunctionCalling(enable: Boolean)

// The LLM will output tool calls in OpenAI format:
// {"tool_calls": [{"name": "get_contacts", "arguments": "{\"limit\": 10}"}]}
```

#### 1.1b Alternative: llama-server HTTP Integration

**Option 2**: Use llama.cpp's built-in HTTP server with native tool calling support (PR #9639)

**Components**:
- Run `llama-server` as a subprocess or native binary
- Configure with `--jinja` flag for tool calling
- Communicate via OpenAI-compatible API

**Implementation**:
```kotlin
class LlamaServerIntegration {
    fun startServer(modelPath: String) {
        // Start llama-server with tool support
        val command = listOf(
            "./llama-server",
            "--model", modelPath,
            "--jinja",  // Enable tool calling
            "-fa",      // Flash attention
            "--port", "8080"
        )
        ProcessBuilder(command).start()
    }
    
    suspend fun chatWithTools(prompt: String, tools: List<MCPTool>): Response {
        // Convert MCP tools to OpenAI format and POST to localhost:8080
        val request = buildOpenAIRequest(prompt, tools)
        return httpClient.newCall(request).execute()
    }
}
```

**Benefits**: 
- Native tool calling with "lazy grammars"
- Supports multiple model formats (Llama 3.x, Functionary, Hermes)
- No custom parsing needed

#### 1.2 Model Selection and Management

**Supported models** (in order of implementation):
1. **Phi-3-mini** (3.8B parameters) - Good balance of size and capability
2. **Gemma-2B** - Optimized for mobile, Google's architecture
3. **Llama-3.2-1B** - Smallest Llama model with function calling
4. **Custom quantized models** - User-provided GGUF files

**Model storage**:
- Ship with quantized Phi-3-mini (Q4_K_M) ~2GB
- Download additional models on-demand
- Support external storage for larger models

### Phase 2: Tool Discovery and System Prompt Generation

#### 2.1 Dynamic Tool Discovery

**Tool List Fetching**:
```kotlin
class LLMToolManager(private val httpClient: OkHttpClient) {
    suspend fun fetchAvailableTools(): List<MCPTool> {
        val request = Request.Builder()
            .url("http://localhost:11434/mcp/tools/list")
            .get()
            .build()
            
        return httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                // Parse JSON-RPC response
                val body = response.body?.string() ?: ""
                parseMCPToolsResponse(body)
            } else {
                emptyList()
            }
        }
    }
    
    fun generateSystemPrompt(tools: List<MCPTool>): String {
        return buildString {
            appendLine("You have access to the following tools via HTTP calls:")
            tools.forEach { tool ->
                appendLine("- ${tool.name} (server: ${tool.serverId}): ${tool.description}")
                appendLine("  Parameters: ${tool.inputSchema}")
            }
            appendLine("\nTo use a tool, make an HTTP POST to http://localhost:11434/mcp/tools/call")
            appendLine("with JSON-RPC format including serverId, tool name, and arguments.")
        }
    }
}
```

#### 2.2 Tool Call Detection and Execution

**LLM Output Parsing**:
```kotlin
class ToolCallParser {
    // Parse structured tool calls from LLM output
    fun parseToolCalls(llmOutput: String): List<ToolCall> {
        // llama.cpp with function calling outputs in this format:
        // <tool_call>{"name": "get_contacts", "arguments": {"limit": 10}}</tool_call>
        val pattern = "<tool_call>(.*?)</tool_call>".toRegex()
        return pattern.findAll(llmOutput).map { match ->
            val json = match.groupValues[1]
            parseToolCallJson(json)
        }.toList()
    }
}

class ToolExecutor(private val httpClient: OkHttpClient) {
    suspend fun executeToolCall(toolCall: ToolCall): String {
        // Make HTTP call to our MCP server
        val request = Request.Builder()
            .url("http://localhost:11434/mcp/tools/call")
            .post(
                """
                {
                    "jsonrpc": "2.0",
                    "id": "${UUID.randomUUID()}",
                    "method": "tools/call",
                    "params": {
                        "serverId": "${toolCall.serverId}",
                        "name": "${toolCall.name}",
                        "arguments": ${toolCall.arguments}
                    }
                }
                """.toRequestBody("application/json".toMediaType())
            )
            .build()
            
        return httpClient.newCall(request).execute().use { response ->
            response.body?.string() ?: "Error executing tool"
        }
    }
}
```

### Phase 3: User Interface Implementation

#### 3.1 Chat Interface

**Core Components**:
```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToServers: () -> Unit
) {
    LaunchedEffect(Unit) {
        // Start MCP HTTP server when chat screen loads
        viewModel.startMCPServer()
    }
    
    Column {
        // Server connection status bar
        MCPServerStatusBar(
            httpServerRunning = viewModel.httpServerRunning,
            connectedServers = viewModel.connectedServers,
            onClick = onNavigateToServers
        )
        
        // Message list with lazy loading
        LazyColumn(
            reverseLayout = true,
            state = rememberLazyListState()
        ) {
            items(viewModel.messages) { message ->
                ChatMessageItem(
                    message = message,
                    onToolResultClick = { /* Show tool execution details */ }
                )
            }
        }
        
        // Input field with streaming indicator
        ChatInputField(
            enabled = !viewModel.isGenerating,
            onSendMessage = viewModel::sendMessage
        )
    }
}
```

**Chat ViewModel Integration**:
```kotlin
class ChatViewModel(
    private val mcpClient: McpClient,
    private val llamaEngine: LlamaEngine
) : ViewModel() {
    private lateinit var httpServer: MCPHttpServer
    
    fun startMCPServer() {
        httpServer = MCPHttpServer(
            port = 11434,
            discovery = mcpClient.discovery,
            connectionManager = mcpClient.connectionManager
        )
        httpServer.startServer()
        
        // Configure LLM to use the HTTP endpoint
        llamaEngine.setToolCallEndpoint("http://localhost:11434")
    }
    
    suspend fun sendMessage(text: String) {
        // Fetch available tools and create system prompt
        val tools = fetchAvailableTools()
        val systemPrompt = generateToolSystemPrompt(tools)
        
        // Generate response with tool calling capability
        llamaEngine.generateWithTools(
            prompt = text,
            systemPrompt = systemPrompt,
            onToken = { token -> /* Handle streaming */ },
            onToolCall = { toolCall -> executeToolCall(toolCall) }
        )
    }
}
```

#### 3.2 MCP Server Management

**Simple Server Discovery UI**:
```kotlin
@Composable
fun MCPServersScreen(viewModel: MCPServersViewModel) {
    LazyColumn {
        // HTTP Server Status
        item {
            Card {
                Row {
                    Text("MCP HTTP Server")
                    Spacer(Modifier.weight(1f))
                    Text(if (viewModel.httpServerRunning) "Running on :11434" else "Stopped")
                }
            }
        }
        
        // Available servers section
        items(viewModel.availableServers) { server ->
            MCPServerItem(
                server = server,
                isConnected = viewModel.isConnected(server.id),
                onToggleConnection = { viewModel.toggleServerConnection(server.id) }
            )
        }
    }
}
```

### Phase 4: Complete Integration Architecture

#### 4.1 Two HTTP Servers Architecture

Our app runs TWO HTTP servers:
1. **MCP HTTP Server** (port 11434) - Our existing `MCPHttpServer` that bridges to AIDL
2. **llama-server** (port 8080) - llama.cpp's server with native tool calling

```text
┌─────────────────────────────────────────┐
│ Android App                             │
│ ┌─────────────────────────────────────┐ │
│ │ Chat UI & Orchestration             │ │
│ └─────────────────────────────────────┘ │
│              ↓ ↑                        │
│ ┌─────────────────────────────────────┐ │
│ │ llama-server (:8080)                │ │
│ │ • Receives prompts + tool schemas   │ │
│ │ • Outputs tool calls in JSON        │ │
│ └─────────────────────────────────────┘ │
│              ↓ ↑                        │
│ ┌─────────────────────────────────────┐ │
│ │ Tool Executor (our code)            │ │
│ │ • Parses tool calls from LLM        │ │
│ │ • Makes HTTP calls to :11434        │ │
│ └─────────────────────────────────────┘ │
│              ↓ ↑                        │
│ ┌─────────────────────────────────────┐ │
│ │ MCP HTTP Server (:11434)            │ │
│ │ • Receives tool execution requests  │ │
│ │ • Translates to AIDL calls          │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
              ↓ ↑ AIDL
┌─────────────────────────────────────────┐
│ 3rd Party Apps (PhoneMCPService, etc.) │
└─────────────────────────────────────────┘
```

#### 4.2 Complete Request Flow

```text
1. User sends message in chat UI
2. App fetches available tools from MCP HTTP Server (:11434)
3. App sends prompt + tool schemas to llama-server (:8080)
4. llama-server generates response with tool calls
5. App parses tool calls from LLM response
6. App executes tools via MCP HTTP Server (:11434)
7. MCP HTTP Server translates to AIDL and calls 3rd party app
8. Tool results return through the chain
9. App sends tool results back to llama-server as context
10. llama-server continues generation with tool results
```

#### 4.2 Error Handling

**Simple Error Recovery**:
```kotlin
class MCPErrorHandler {
    fun handleHttpError(error: Exception): String {
        return when (error) {
            is ConnectException -> "MCP server not running. Please restart the app."
            is TimeoutException -> "Tool execution timed out. Try again."
            is SecurityException -> "Permission denied. Grant required permissions."
            else -> "Tool execution failed: ${error.message}"
        }
    }
}
```

### Phase 5: Testing and Polish

#### 5.1 Integration Testing

**Key Test Scenarios**:
1. LLM successfully discovers available tools via HTTP
2. Tool execution works end-to-end
3. Permission requests are handled correctly
4. Server disconnection is handled gracefully
5. Multiple tool calls in sequence work properly

#### 5.2 Performance Optimization

**Simple Optimizations**:
- Cache tool list for 60 seconds
- Reuse HTTP connections with connection pooling
- Implement request timeouts (30s for tools)
- Monitor memory usage and clear old conversations

## Technical Specifications

### Dependencies

```toml
# In gradle/libs.versions.toml
[versions]
# ... existing versions ...
cmake = "3.22.1"
llama-cpp = "latest"
navigation-compose = "2.7.7"
datastore-preferences = "1.0.0"
coil-compose = "2.5.0"

[libraries]
# ... existing libraries ...
# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
# Preferences
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore-preferences" }
# Image loading for chat UI
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil-compose" }
# Markdown rendering for formatted responses
markdown-android = { group = "io.noties.markwon", name = "core", version = "4.6.2" }
```

### Model Requirements

**Minimum Viable Model**:
- Size: 1-4GB quantized
- RAM: 4-6GB available
- Storage: 8GB free space
- Architecture: ARM64-v8a

**Recommended Configuration**:
- Device: 8GB+ RAM
- Storage: 16GB+ free
- Android: 12+ (API 31+)

### Performance Targets

**Inference Speed**:
- First token: < 2 seconds
- Subsequent tokens: 10-30 tokens/second
- Tool execution: < 500ms overhead

**Resource Usage**:
- CPU: < 80% on big cores
- Memory: < 4GB for model + 1GB working
- Battery: < 10% drain per hour active use

## Testing Strategy

### Unit Tests

- LLM JNI wrapper functions
- Function call parser accuracy
- MCP tool schema generator
- Error recovery mechanisms

### Integration Tests

- End-to-end chat with tool use
- MCP server discovery and connection
- Permission request flows
- Model switching and cleanup

### Performance Tests

- Token generation benchmarks
- Memory leak detection
- Battery usage profiling
- Thermal behavior analysis

### User Acceptance Tests

- Chat responsiveness
- Tool execution clarity
- Error message helpfulness
- Overall user experience

## Risk Mitigation

### Technical Risks

1. **Model Size**: Provide cloud-assisted mode for smaller devices
2. **Performance**: Implement aggressive caching and context management
3. **Compatibility**: Test on wide range of devices and Android versions
4. **Battery**: Add power-saving mode with reduced model size

### User Experience Risks

1. **Complexity**: Progressive disclosure of advanced features
2. **Expectations**: Clear communication about local vs cloud capabilities
3. **Privacy**: Prominent privacy notices and data handling options

## Key Insights and Simplifications

### The HTTP Bridge Already Exists!

Our `mmcp-client-android` library already provides:
- **MCPHttpServer**: Runs on port 11434, exposes standard MCP endpoints
- **AIDL Discovery**: Finds MCP servers from 3rd party apps automatically
- **HTTP-to-AIDL Bridge**: Translates HTTP requests to AIDL service calls
- **JSON-RPC Protocol**: Properly formatted requests and responses

### How Tool Calling Actually Works

Based on research into llama.cpp, Claude Desktop, and Cursor implementations:

**LLMs don't make HTTP calls directly!** Instead:
1. The LLM generates structured output indicating tool calls
2. The host application (our app) parses this output
3. The host executes the tool calls via HTTP/stdio
4. The host feeds results back to the LLM as context

**Key Architecture Pattern** (used by Claude Desktop & Cursor):
- Host app spawns MCP servers as child processes (stdio)
- Or connects to HTTP endpoints (our approach)
- Host manages all MCP communication
- LLM only sees prompts and results

## Success Criteria

1. **Functional**: LLM successfully discovers and uses MCP tools via HTTP
2. **Performance**: Achieves 10-30 tokens/second on modern devices
3. **Usability**: Users can chat and see tool executions clearly
4. **Reliability**: HTTP server stays running, handles errors gracefully
5. **Compatibility**: Works on Android 12+ devices with 6GB+ RAM

## Implementation Priority

1. **Phase 1**: Get llama.cpp running with basic inference
2. **Phase 2**: Configure HTTP tool calling in llama.cpp
3. **Phase 3**: Build simple chat UI
4. **Phase 4**: Add MCP server discovery UI
5. **Phase 5**: Polish and optimize

## Future Enhancements

1. **Advanced Models**: Support for larger, more capable models
2. **Voice Interface**: Speech input/output for hands-free operation
3. **Remote MCP**: Connect to MCP servers over the network
4. **iOS Port**: Adapt the simplified architecture for iOS

## Conclusion

This implementation plan leverages our existing MCP HTTP server infrastructure to provide a clean integration path for local LLMs. By recognizing that we already built the HTTP-to-AIDL bridge in `mmcp-client-android`, we can focus on:

1. Getting llama.cpp running with HTTP tool calling
2. Building a great chat UI
3. Making the MCP server discovery experience smooth

The architecture is simple, maintainable, and aligns perfectly with how mobile LLMs actually work today - through HTTP APIs rather than complex protocols.