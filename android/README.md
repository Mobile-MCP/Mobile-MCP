# Android Integration Guide

Complete implementation guide for the Mobile MCP Framework on Android.

> 👈 New to Mobile MCP? Start with the [main project overview](../README.md)

This guide covers everything you need to integrate MCP capabilities in your Android apps, whether you're building an LLM app that needs access to local tools or a regular app that wants to expose capabilities to LLMs.

## Module Structure

```
android/
├── mmcp-client-android/       # Client library for LLM apps
│   ├── src/main/kotlin/       # HTTP server, discovery, direct API
├── mmcp-server-android/       # Server framework for 3rd party apps
│   ├── src/main/kotlin/       # Annotations, base service, registry
│   └── src/androidTest/       # Integration tests
├── example/                   # Example client app demonstrating discovery and mcp tool usage
├── phonemcpserver/            # Example MCP server app exposing phone features
```

## Two Library Architecture

### `mmcp-client-android` - For LLM Apps

The client library (`io.rosenpin.mcp:mmcp-client-android`) provides:

- **HTTP Server**: NanoHTTPD-based MCP endpoint server (port 11434)
- **Discovery Engine**: Intent-based Android MCP server discovery
- **Direct API**: In-process tool calling for MLKit-style LLMs (WIP)
- **Transport Bridge**: AIDL-to-HTTP/Direct call abstraction

### `mmcp-server-android` - For 3rd Party Apps

The server framework (`io.rosenpin.mcp:mmcp-server-android`) provides:

- **Annotations**: `@MCPServer`, `@MCPTool`, `@MCPResource` for easy integration
- **Code Generation**: KAPT processor for AIDL service generation
- **Build Integration**: Gradle plugin for manifest entries and permissions
- **Runtime Support**: Base classes and utilities for generated services

## Integration Guide

### For LLM App Developers

If you're building an LLM app (like Ollama, MLKit integration, etc.) and want access to local MCP servers:

#### 1. Add Client Library Dependency

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    implementation("io.rosenpin.mcp:mmcp-client-android:1.0.0")
}
```

#### 2. Add Intents for discovery to Manifest

```xml
<!-- AndroidManifest.xml -->
  <!-- Package visibility for MCP server discovery (Android 11+) -->
    <queries>
        <intent>
            <action android:name="io.rosenpin.mmcp.action.MCP_SERVICE" />
        </intent>
        <intent>
            <action android:name="io.rosenpin.mmcp.action.MCP_TOOL_SERVICE" />
        </intent>
        <intent>
            <action android:name="io.rosenpin.mmcp.action.MCP_RESOURCE_SERVICE" />
        </intent>
        <intent>
            <action android:name="io.rosenpin.mmcp.action.MCP_PROMPT_SERVICE" />
        </intent>
        <intent>
            <action android:name="io.rosenpin.mmcp.action.MCP_DISCOVERY_SERVICE" />
        </intent>
    </queries>
```

#### 3. Implement MCP Client

```kotlin
class MyLLMActivity : ComponentActivity() {
    private lateinit var mcpClient: MobileMCPClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MCP client
        lifecycleScope.launch {
            mcpClient = MobileMCPClient(this@MyLLMActivity)
            
            // Start HTTP server (defaults to port 11434 - Ollama compatible)
            val httpServer = mcpClient.startHttpServer(port = 11434)
            
            // Discover available MCP servers
            val servers = mcpClient.discoverServers()
            Log.d("MCP", "Found ${servers.size} MCP servers")
            
            // Your LLM can now make HTTP calls to these endpoints:
            // http://localhost:11434/mcp/tools/list        - List all available tools
            // http://localhost:11434/mcp/tools/call        - Call a specific tool
            // http://localhost:11434/mcp/resources/list    - List all resources
            // http://localhost:11434/mcp/resources/read    - Read a specific resource
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            mcpClient.stopHttpServer()
        }
    }
}
```

#### 4. HTTP API Usage

Your LLM can make standard HTTP requests:

```kotlin
// List all available tools across all servers
val toolsResponse = httpClient.get("http://localhost:11434/mcp/tools/list")
// Returns: { "tools": [{"name": "search_files", "description": "...", "server": "com.fileapp.mcp"}] }

// Call a specific tool
val callResponse = httpClient.post("http://localhost:11434/mcp/tools/call") {
    setBody("""{"tool": "search_files", "server": "com.fileapp.mcp", "params": {"query": "documents"}}""")
}
// Returns: { "result": [...] }
```

#### 5. Direct API Usage (Alternative)

For more control, use the direct API instead of HTTP:

```kotlin
// Direct tool calling (bypasses HTTP layer)
val result = mcpClient.callTool(
    serverId = "com.fileapp.mcp",
    toolName = "search_files", 
    params = JsonObject().apply { addProperty("query", "documents") }
)

// Direct resource access
val resource = mcpClient.getResource(
    serverId = "com.fileapp.mcp",
    uri = "file:///storage/emulated/0/Documents/readme.txt"
)
```

### For App Developers (Exposing Tools)

If you have an existing Android app and want to expose its capabilities to LLMs:

#### 1. Add Server Framework Dependencies

```kotlin
// build.gradle.kts (Module: app)
plugins {
    id("io.rosenpin.mcp.android") version "1.0.0"  // Generates AIDL & manifest
}

dependencies {
    implementation("io.rosenpin.mcp:mmcp-server-android:1.0.0")
    kapt("io.rosenpin.mcp:mmcp-server-android:1.0.0")  // Processes @MCPServer annotations
}
```

#### 2. Define Your MCP Server

```kotlin
@MCPServer(
    id = "com.myapp.filemanager",           // Unique server ID
    name = "File Manager",                  // Human-readable name
    description = "Provides file system operations",
    version = "1.0.0"
)
class FileManagerMCPServer: ContextAwareMCPService() {
    
    @MCPTool(
        name = "list_files",
        description = "List files in a directory",
        schema = """{"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}"""
    )
    suspend fun listFiles(@MCPParam("path") path: String): List<FileInfo> {
        // Your existing file listing logic
        return File(path).listFiles()?.map { 
            FileInfo(it.name, it.length(), it.isDirectory()) 
        } ?: emptyList()
    }
    
    @MCPTool(
        name = "create_folder", 
        description = "Create a new folder",
        schema = """{"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}"""
    )
    suspend fun createFolder(@MCPParam("path") path: String): Boolean {
        return File(path).mkdirs()
    }
    
    @MCPResource(scheme = "file")
    suspend fun getFileResource(@MCPParam("uri") uri: String): MCPResource {
        val file = File(URI(uri).path)
        return MCPResource(
            uri = uri,
            name = file.name,
            mimeType = getMimeType(file),
            contents = file.readText()
        )
    }
}
```

#### 4. Register the service in your Manifest  
  ```xml
<!-- MCP Service -->
        <service
            android:name=".PhoneMCPService"
            android:exported="true">
            <intent-filter>
                <action android:name="io.rosenpin.mmcp.action.MCP_SERVICE" />
                <action android:name="io.rosenpin.mmcp.action.MCP_TOOL_SERVICE" />
                <action android:name="io.rosenpin.mmcp.action.MCP_RESOURCE_SERVICE" />
                <action android:name="io.rosenpin.mmcp.action.MCP_PROMPT_SERVICE" />
                <action android:name="io.rosenpin.mmcp.action.MCP_DISCOVERY_SERVICE" />
            </intent-filter>
        </service>
```

#### 4. Build Your App

The build plugin auto-generates:

- **AIDL interface definitions** for your server
- **Android Service implementation** that handles MCP protocol
- **Manifest entries** for service discovery (`io.mmcp.action.SERVER`)
- **Permission declarations** for any sensitive operations

#### 5. Test Your MCP Server

```kotlin
// Your app can test its own MCP server locally
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Test with local MCP client
        lifecycleScope.launch {
            val mcpClient = MobileMCPClient(this@MainActivity)
            mcpClient.startHttpServer()
            
            val servers = mcpClient.discoverServers()
            val myServer = servers.find { it.id == "com.myapp.filemanager" }
            Log.d("MCP", "My server discoverable: ${myServer != null}")
        }
    }
}
```

#### 6. Advanced Features

**Multiple Resource Schemes:**

```kotlin
@MCPServer(id = "com.myapp.media", name = "Media Manager")
class MediaMCPServer {
    
    @MCPResource(scheme = "photo")
    suspend fun getPhoto(@MCPParam("uri") uri: String): MCPResource { ... }
    
    @MCPResource(scheme = "video") 
    suspend fun getVideo(@MCPParam("uri") uri: String): MCPResource { ... }
}
```

**Permission-Based Tools:**

```kotlin
@MCPTool(
    name = "read_contacts",
    description = "Read device contacts",
    requiredPermissions = ["android.permission.READ_CONTACTS"]
)
suspend fun readContacts(): List<Contact> { ... }
```

**Context-Aware Tools:**

```kotlin
@MCPServer(id = "com.myapp.location", name = "Location Services")
class LocationMCPServer(private val context: Context) {
    
    @MCPTool(name = "get_location")
    suspend fun getCurrentLocation(): Location {
        // Use context to access location services
        return locationManager.getCurrentLocation()
    }
}

## Development Setup

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (API level 24)
- Kotlin 1.9+
- Gradle 8.0+

### Building the Library

```bash
cd android/
./gradlew :mmcp-client-android:build :mmcp-server-android:build
```

### Running Tests

```bash
# Unit tests
./gradlew :mmcp-client-android:test :mmcp-server-android:test

# Integration tests (requires device/emulator)
./gradlew :mmcp-client-android:connectedAndroidTest :mmcp-server-android:connectedAndroidTest
```

### Example Apps

The project includes two example applications:

#### Client Example (`example/`)

A full-featured client app demonstrating:

- MCP server discovery
- Dynamic UI generation for discovered tools
- Tool execution with parameter input
- Real-time connection status

```bash
./gradlew :example:installDebug
```

#### Phone MCP Server (`phonemcpserver/`)

An example MCP server exposing phone features:

- Contact access tools
- Call history access
- SMS tools (with proper permissions)
- Demonstrates the `@MCPServer` annotation system

```bash
./gradlew :phonemcpserver:installDebug
```

## Architecture Details

### HTTP Server Implementation

- Uses NanoHTTPD for lightweight HTTP serving
- Runs on port 11434 (Ollama-compatible)
- Lifecycle tied to LLM app activity
- Endpoints follow MCP specification

### AIDL Discovery

- Intent action: `io.mmcp.action.SERVER`
- Categories for capability grouping
- Metadata for server information
- Package visibility queries (Android 11+)

### Security Model

- Signature-level permissions for trusted ecosystems
- Runtime permissions for sensitive operations
- Caller verification via Binder.getCallingUid()
- Permission scoping per tool/resource

### Threading Model

- All operations async with Kotlin coroutines
- Background thread pool for heavy operations
- Main thread for UI updates only
- Memory-bandwidth optimized (big cores only)

## API Reference

### MobileMCPClient

```kotlin
class MobileMCPClient(context: Context) {
    suspend fun startHttpServer(port: Int = 11434): MCPHttpServer
    suspend fun stopHttpServer()
    suspend fun discoverServers(): List<MCPServerInfo>
    suspend fun callTool(serverId: String, toolName: String, params: JsonObject): JsonElement
    suspend fun getResource(serverId: String, uri: String): MCPResource
    fun createStdioTransport(): Transport? // Future-proofing
}
```

### Annotations

```kotlin
@MCPServer(id: String, name: String, description: String, version: String)
@MCPTool(name: String, description: String, schema: String)
@MCPResource(scheme: String)
@MCPParam(name: String)
```

## Performance Considerations

- **Discovery**: Sub-100ms server discovery target
- **Memory**: Efficient AIDL pooling, avoid memory leaks
- **Battery**: HTTP server only when LLM active
- **Threading**: Use big cores only for inference operations
- **Caching**: Cache server capabilities and connections

## Debugging

### Logging

Enable debug logging:

```kotlin
MobileMCPClient.enableDebugLogging(true)
```

### ADB Commands

```bash
# List MCP services
adb shell dumpsys package queries | grep mmcp

# Check running services
adb shell dumpsys activity services | grep MCP
```

### Testing Tools

- MCP Server validator (planned)
- HTTP endpoint testing utilities
- AIDL connection debugging

## Contributing to Android Implementation

See the [main contributing guide](../README.md#contributing) for general information.

**Android-specific guidelines:**

- Follow Android Kotlin style guide
- Use ktlint for formatting: `./gradlew ktlintFormat`
- Document public APIs with KDoc
- Add unit tests for new features
- Test on multiple Android versions (API 24+)

## Troubleshooting

### Common Issues

**Server Discovery Fails**

- Check package visibility queries in manifest
- Verify Intent filters are correctly declared
- Ensure target app is installed and MCP service enabled

**HTTP Server Won't Start**

- Check port availability (11434)
- Verify network permissions
- Ensure not running on main thread

**AIDL Binding Fails**

- Check service is exported and running
- Verify permissions are granted
- Test with `adb shell dumpsys activity services`

## Current Progress


https://github.com/user-attachments/assets/b02066a0-f85b-42e8-9a98-174ca7e9d7a3


### What's Completed ✅

- **Core Client Library (`mmcp-client-android`)**
  - HTTP server running on port 11434 (Ollama-compatible)
  - AIDL-based service discovery and connection management
  - Direct API for in-process tool calling
  - Full MCP protocol compliance with JSON-RPC 2.0
  
- **Core Server Framework (`mmcp-server-android`)**
  - Base service class (`MCPServiceBase`) with full AIDL implementation
  - Annotation system (`@MCPServer`, `@MCPTool`, `@MCPResource`, `@MCPPrompt`)
  - Annotation processor for metadata extraction
  - Method registry for dynamic invocation
  
- **Working Examples**
  - Client example app with full discovery and tool execution
  - Phone MCP server demonstrating real-world integration
  
- **AIDL Infrastructure**
  - Complete AIDL interfaces for all MCP capabilities
  - Service discovery via Android Intent system
  - Proper marshalling/unmarshalling of data

### What's In Progress 🚧

- **LLM Integration** ([PR #12](https://github.com/Mobile-MCP/Mobile-MCP/pull/12))
  - Embedding locally-running LLM with MCP support
  - Integration with our HTTP server infrastructure
  - Tool calling orchestration between LLM and MCP servers

### What's Next ⏳

- Gradle plugin for automatic manifest generation
- Advanced permission management system
- Performance optimizations and caching
- iOS implementation
- Developer tools and debugging utilities

---

📖 **Next Steps:** [Architecture Deep-Dive](../docs/mobile-mcp-architecture.md) | [Main Project](../README.md)
