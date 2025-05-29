# Mobile MCP Framework Architecture

## Project Overview

Create a **Mobile MCP Framework** enabling locally-running LLMs on mobile devices to discover and use MCP servers from 3rd party apps. The framework consists of:

1. **Mobile MCP Client Library**: For LLM apps to discover and communicate with local MCP servers
2. **Mobile MCP Server Framework**: For 3rd party apps to easily expose MCP capabilities  
3. **Discovery Protocol**: Mobile-optimized service discovery via manifests and intent filters

**Core Vision**: LLMs get MCP-compatible interfaces (HTTP/direct calls/stdio) while underneath we use mobile-optimized transport (AIDL/URL schemes).

## Architecture Overview

### Two-Sided Framework Design

```text
┌─────────────────────────────────────────┐
│ Mobile LLM App (Ollama, MLKit, etc.)    │
│ ┌─────────────────────────────────────┐ │
│ │ Current Reality:                    │ │
│ │ • HTTP calls to localhost:11434     │ │  
│ │ • Direct library function calls     │ │
│ │ Future: stdio/SSE when available    │ │
│ └─────────────────────────────────────┘ │
│               │                         │
│ ┌─────────────────────────────────────┐ │
│ │ Mobile MCP Client Library           │ │ ← THE CORE VALUE
│ │ • HTTP Server (NanoHTTPD) PRIMARY   │ │
│ │ • Direct call API for in-process    │ │
│ │ • stdio Transport (future-proof)    │ │
│ │ • AIDL service discovery & binding  │ │
│ │ • Perfect MCP protocol compliance   │ │
│ │ • Runs only when LLM app is active  │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
                │
                │ AIDL Discovery & Communication
                │
┌─────────────────────────────────────────┐
│ 3rd Party App (File Manager, etc.)     │
│ ┌─────────────────────────────────────┐ │
│ │ Mobile MCP Server Framework         │ │ ← SUPPORTING INFRASTRUCTURE
│ │ • @MCPServer annotations            │ │
│ │ • Auto-generated AIDL service       │ │
│ │ • Intent filter discovery           │ │
│ │ • Permission integration            │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

## Client Library: The Core Innovation

### API Design (HTTP-First with Future-Proofing)

```kotlin
// Mobile MCP Client Library API
class MobileMCPClient {
    // PRIMARY: HTTP server for current mobile LLMs (Ollama, etc.)
    suspend fun startHttpServer(port: Int = 11434): MCPHttpServer
    suspend fun stopHttpServer()
    
    // SECONDARY: Direct calls for in-process LLMs (MLKit, etc.)
    suspend fun discoverServers(): List<MCPServerInfo>
    suspend fun callTool(serverId: String, toolName: String, params: JsonObject): JsonElement
    suspend fun getResource(serverId: String, uri: String): MCPResource
    
    // FUTURE-PROOF: Standard MCP transports when mobile LLMs support them
    fun createStdioTransport(): Transport? // Returns null on iOS or unsupported
    fun createHttpTransport(remoteUrl: String): Transport // For remote MCP servers
}

// HTTP Server Implementation
class MCPHttpServerImpl(
    private val discovery: AndroidMCPDiscovery,
    private val port: Int = 11434
) : MCPHttpServer {
    private val httpServer = NanoHTTPD(port)
    
    // HTTP endpoints that mobile LLMs can call
    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/mcp/tools/list" -> handleToolsList()
            "/mcp/tools/call" -> handleToolCall(session)
            "/mcp/resources/list" -> handleResourcesList()
            "/mcp/resources/read" -> handleResourceRead(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
}
```

### Discovery Protocol

```kotlin
// Android Discovery via Intent Queries
class AndroidMCPDiscovery {
    suspend fun discoverServers(): List<MCPServerInfo> {
        val intent = Intent("io.mmcp.action.SERVER")
        val services = packageManager.queryIntentServices(intent, 0)
        
        return services.map { resolveInfo ->
            MCPServerInfo(
                id = resolveInfo.serviceInfo.packageName,
                name = resolveInfo.loadLabel(packageManager).toString(),
                capabilities = parseManifestCapabilities(resolveInfo),
                permissions = extractRequiredPermissions(resolveInfo)
            )
        }
    }
}
```

## Server Framework: Supporting Infrastructure

### Developer Experience

```kotlin
// What 3rd party developers write
@MCPServer(
    id = "com.filemanager.mcp",
    name = "File Manager MCP",
    description = "Provides file system operations",
    version = "1.0.0"
)
class FileManagerMCPServer {
    
    @MCPTool(
        name = "list_files",
        description = "List files in directory",
        schema = """{"type": "object", "properties": {"path": {"type": "string"}}}"""
    )
    suspend fun listFiles(@MCPParam("path") path: String): List<FileInfo> {
        return File(path).listFiles()?.map { 
            FileInfo(it.name, it.length(), it.isDirectory()) 
        } ?: emptyList()
    }
    
    @MCPResource(scheme = "file")
    suspend fun getFileResource(@MCPParam("uri") uri: String): MCPResource {
        val file = File(URI(uri).path)
        return MCPResource(
            uri = uri,
            name = file.name,
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension),
            contents = file.readText()
        )
    }
}
```

### Auto-Generated Infrastructure

**AIDL Interface** (generated):

```java
interface IFileManagerMCP {
    String processMCPRequest(String jsonRpcRequest);
    void registerCallback(IMCPCallback callback);
    MCPCapabilities getCapabilities();
}
```

**Android Service** (generated):

```kotlin
class FileManagerMCPService : Service() {
    private val server = FileManagerMCPServer()
    private val protocolCore = MCPProtocolCore()
    
    private val binder = object : IFileManagerMCP.Stub() {
        override fun processMCPRequest(jsonRpcRequest: String): String {
            return protocolCore.processRequest(jsonRpcRequest, server)
        }
        
        override fun getCapabilities(): MCPCapabilities {
            return protocolCore.getCapabilities(server)
        }
    }
    
    override fun onBind(intent: Intent): IBinder = binder
}
```

**Manifest Entries** (auto-generated):

```xml
<service android:name=".FileManagerMCPService" 
         android:exported="true">
    <intent-filter>
        <action android:name="io.mmcp.action.SERVER" />
        <category android:name="io.mmcp.category.FILESYSTEM" />
        <data android:scheme="file" />
    </intent-filter>
    <meta-data android:name="mcp.capabilities" 
               android:value="tools:list_files;resources:file" />
    <meta-data android:name="mcp.version" 
               android:value="1.0.0" />
</service>

<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## Technical Decisions

### Research Findings Integration

Based on research into MCP protocol and mobile LLM architectures:

1. **MCP Protocol**: Uses JSON-RPC 2.0 over stdio (primary) or HTTP+SSE (remote)
2. **Mobile LLM Reality**: Current frameworks use HTTP (Ollama) or direct calls (MLKit), not stdio
3. **Platform Constraints**: No subprocesses on iOS, limited on Android - single-process design required
4. **Threading Model**: Memory-bandwidth bound, use big cores only, async-native with coroutines
5. **Transport Strategy**: HTTP-first for current compatibility, stdio future-proofing

### Android Architecture

- **Transport**: HTTP server (NanoHTTPD) + AIDL backend + stdio future-proofing
- **Discovery**: Intent filters with Android 11+ package visibility queries
- **Security**: Signature-level permissions + runtime permission requests
- **Background**: Bound services with proper lifecycle management

### Cross-Platform Strategy

- **Framework**: KMP for shared protocol core (30% complex logic)
- **Platform-specific**: Build tooling, annotation processing, transport (70%)
- **Android**: AIDL + Intent filters + Gradle plugin + KAPT processor
- **iOS**: URL schemes + App Intents + Swift macros + SPM plugin (future)

### Protocol Compatibility

- Keep core MCP protocol unchanged (JSON-RPC 2.0)
- HTTP-first transport for current mobile LLM compatibility
- stdio transport available when mobile frameworks support it
- Support capability negotiation and version compatibility

## Implementation Strategy

### Phase 1: Client Library Foundation (Priority: HIGH)

1. **Mobile MCP Client Library**
   - HTTP server with MCP endpoints (tools/list, tools/call, etc.)
   - Android server discovery via Intent queries
   - AIDL-to-HTTP bridge
   - Direct call API for in-process LLMs

2. **KMP Protocol Core**
   - JSON-RPC 2.0 parsing/serialization with type safety
   - MCP state machine (initialization → capabilities → tools/resources)
   - Request/response correlation with timeouts and retry logic
   - Transport abstraction layer

3. **Android Server Framework (Basic)**
   - `@MCPServer` annotation for easy server creation
   - AIDL service auto-generation
   - Manifest entry auto-generation for discovery

### Phase 2: Advanced Features (Priority: MEDIUM)

1. **Enhanced Android Client**
   - Permission management and user consent flows
   - Service binding optimization and error recovery
   - Caching and offline capability support
   - Performance monitoring and debugging tools

2. **Advanced Android Server Framework**
   - Runtime 3rd party app integration via manifests
   - Dynamic capability discovery and loading
   - Advanced security and permission scoping
   - Background service lifecycle management

3. **iOS Basic Support**
   - iOS MCP client library (URL scheme-based)
   - `@MCPServer` annotations for compile-time integration
   - In-app server coordination (same-developer only)

### Phase 3: Ecosystem & Polish (Priority: LOW)

1. **Remote MCP Support**
   - HTTP/WebSocket transport for remote servers
   - Authentication and secure connections
   - Hybrid local/remote server discovery

2. **Developer Tools & Documentation**
   - MCP server testing and debugging tools
   - Comprehensive developer documentation
   - Example implementations and tutorials

## Directory Structure

```text
mmcp/
├── shared/                    # KMP protocol core
│   ├── protocol/             # JSON-RPC + MCP state machine
│   ├── correlation/          # Request/response correlation  
│   └── registry/             # Tool/resource registration
├── android/                  # Android implementation
│   ├── mmcp-client-android/  # Client library for LLM apps
│   │   ├── discovery/        # Intent-based server discovery
│   │   ├── http-server/      # NanoHTTPD MCP endpoints
│   │   └── transport/        # AIDL-to-HTTP bridge
│   └── mmcp-server-android/  # Server framework for 3rd party apps
│       ├── annotations/      # @MCPServer, @MCPTool definitions
│       ├── processor/        # KAPT annotation processor
│       ├── gradle-plugin/    # Build system integration
│       └── runtime/          # Generated service base classes
├── ios/                      # iOS implementation
│   ├── swift-macros/         # Code generation
│   ├── spm-plugin/           # Build system integration
│   ├── runtime/              # URL schemes + App Intents
│   └── library/              # Developer-facing API
├── research/                 # Research documents and findings
├── docs/                     # Architecture documentation
└── examples/                 # Sample implementations
    ├── android-fileserver/   # mmcp-server-android usage example
    ├── android-llm-app/      # mmcp-client-android usage example
    └── ios-fileserver/       # iOS @MCPProvider example (future)
```

## Implementation Timeline

1. **Phase 1**: KMP protocol core + Android HTTP client library
2. **Phase 2**: Android annotation processor + AIDL generation + server discovery
3. **Phase 3**: Advanced Android features + iOS basic support
4. **Phase 4**: Remote MCP support + developer tools
5. **Phase 5**: Polish, documentation, examples, and ecosystem development

## Success Metrics

- **For LLM App Developers**: Import client library, start HTTP server, get local MCP tool access
- **For 3rd Party Developers**: Add `@MCPServer` annotations, get discoverable MCP service automatically
- **Protocol Compatibility**: Existing MCP-compatible LLMs work when they add HTTP support
- **Discovery Efficiency**: Sub-100ms server discovery and connection establishment
- **Developer Experience**: Zero-boilerplate integration with clean, native-feeling APIs

## Why This Architecture Works for Mobile

1. **Current Compatibility**: Works with how mobile LLMs actually operate today (HTTP/direct calls)
2. **Future-Proof**: stdio support ready for when mobile LLM frameworks add it
3. **Mobile-Native**: Respects platform constraints (no subprocesses, battery-aware, async-first)
4. **Zero Client Boilerplate**: LLM apps import library, start HTTP server, make calls
5. **Easy Server Creation**: 3rd party apps add annotations, get discoverable AIDL services
6. **Lifecycle-Appropriate**: HTTP server lifecycle matches LLM app lifecycle
7. **Performance-Aware**: Memory-bandwidth optimized, big cores only, comprehensive timeouts

The architecture embraces mobile platform reality while maintaining MCP protocol compatibility and future extensibility.
