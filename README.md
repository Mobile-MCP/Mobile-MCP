# Mobile MCP Framework

> Enabling locally-running LLMs on mobile devices to discover and use tools from 3rd party apps

## What is this?

The Mobile MCP Framework brings the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) to mobile platforms, allowing AI applications to seamlessly integrate with local device capabilities through a standardized interface.

## The Problem

Mobile LLMs are isolated - they can't access your files, contacts, calendar, or other app data. Desktop LLMs use MCP to connect with external tools, but mobile platforms are not as mature yet and have different constraints (no subprocesses, sandboxing, etc).

## The Solution

A **two-sided framework** that bridges this gap:

- **For LLM Apps**: Import `mmcp-client-android`, to get access to locally available MCP servers
- **For App Developers**: Import `mmcp-server-android`, add simple annotations, expose your app's capabilities to any LLM

## Platform Support

| Platform | Compile-time Integration | Runtime Discovery | Status |
|----------|-------------------------|-------------------|---------|
| **Android** | ✅ `@MCPServer` annotations | ✅ Intent-based discovery | **Active Development** |
| **iOS** | 🚧 `@MCPServer` annotations | ❌ Same-developer only | **Planned** |
| **Remote** | 🚧 HTTP/WebSocket | 🚧 URL-based | **Planned** |

## Quick Start

**Get started in 2 minutes:**

```kotlin
// LLM Apps: Add mmcp-client-android dependency
val mcpClient = McpClient(context)
mcpClient.startHttpServer() // Discover and access all local tools

// App Developers: Add mmcp-server-android dependency
@MCPServer(
    id = "io.rosenpin.mcp.phonemcpserver",
    name = "Phone & Contacts MCP Server",
    description = "Provides access to device contacts and phone calling functionality",
    version = "1.0.0"
)
class PhoneMCPService : MCPServiceBase() {
       @MCPTool(
        id = "make_call",
        name = "Make Phone Call",
        description = "Initiate a phone call to the specified number",
        parameters = """
        {
            "type": "object",
            "properties": {
                "phoneNumber": {
                    "type": "string",
                    "description": "Phone number to call"
                }
            },
            "required": ["phoneNumber"]
        }
        """
    )
    fun makeCall(@MCPParam("phoneNumber") phoneNumber: String): String {
        try {
            ...
            telecomManager?.placeCall(Uri.fromParts("tel", phoneNumber, null), null)
            return "Call initiated to $phoneNumber"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            return "Failed to make call: ${e.message}"
        }
    }
}
```

👉 **[Complete Android Integration Guide](android/README.md)**

## Architecture

```
┌─────────────────────┐    HTTP/Direct Calls    ┌─────────────────────┐
│   Mobile LLM App    │ ◄─────────────────────► │ mmcp-client-android │
│  (Ollama, MLKit)    │                         │ (Discovery & HTTP)  │
└─────────────────────┘                         └─────────────────────┘
                                                           │
                                                    AIDL Discovery
                                                           │
                                                           ▼
                                                ┌─────────────────────┐
                                                │  3rd Party Apps     │
                                                │ mmcp-server-android │
                                                │ (@MCPServer tools)  │
                                                └─────────────────────┘
```

## Some Example Use Cases

- **File Manager**: Expose file operations to LLMs
- **Contact App**: Let LLMs search and access contacts  
- **Calendar**: Enable LLMs to read/create events
- **Note-Taking Apps**: Allow LLMs to search through notes
- **Photo Gallery**: Let LLMs analyze and search photos

## Documentation

| Resource | Description |
|----------|-------------|
| **[Android Integration Guide](android/README.md)** | Complete Android implementation guide |
| **[Architecture Deep-Dive](docs/mobile-mcp-architecture.md)** | Technical architecture and design decisions |
| **[Research Findings](research/)** | Mobile LLM integration research and insights |

## Development Status

## Current Progress

<https://github.com/user-attachments/assets/b02066a0-f85b-42e8-9a98-174ca7e9d7a3>

### ✅ What's Completed

- **Research and Architecture**: Complete analysis of mobile LLM landscape and MCP requirements
- **Android Client Library**: HTTP server (port 11434), AIDL discovery, direct API  
- **Android Server Framework**: Base service, annotations, method registry
- **Working Examples**: Client demo app, Phone MCP server
- **AIDL Infrastructure**: Full protocol implementation with proper marshalling

### 🚧 What's In Progress

- **LLM Integration** ([PR #12](https://github.com/Mobile-MCP/Mobile-MCP/pull/12)): Embedding locally-running LLM with MCP support

### ⏳ What's Next

- Gradle plugin for automatic code generation
- iOS implementation
- Remote MCP support using SSE proxying

## Contributing

We welcome contributions! This project aims to become the standard for mobile LLM-tool integration.

**Getting Started:** [Architecture docs](docs/mobile-mcp-architecture.md) → [Android guide](android/README.md) → Open issues

### License

Apache 2.0 - See [LICENSE](LICENSE) for details

## Why Mobile MCP Matters

As LLMs become more capable, they need access to real-world data and tools. Mobile devices are where most computing happens, but they're the most constrained environment. By solving mobile MCP integration, we unlock the full potential of AI assistants on the devices people use most.

**Join us in building the future of mobile AI integration.**
