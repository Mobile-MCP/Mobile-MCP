# Mobile MCP Framework

> Enabling locally-running LLMs on mobile devices to discover and use tools from 3rd party apps

## What is this?

The Mobile MCP Framework brings the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) to mobile platforms, allowing AI applications to seamlessly integrate with local device capabilities through a standardized interface.

## The Problem

Mobile LLMs are isolated - they can't access your files, contacts, calendar, or other app data. Desktop LLMs use MCP to connect with external tools, but mobile platforms have different constraints (no subprocesses, sandboxing, battery concerns).

## The Solution

A **two-sided framework** that bridges this gap:

- **For LLM Apps**: Import `mmcp-client-android`, get instant access to local MCP servers
- **For App Developers**: Import `mmcp-server-android`, add simple annotations, expose your app's capabilities to any LLM

## Platform Support

| Platform | Compile-time Integration | Runtime Discovery | Status |
|----------|-------------------------|-------------------|---------|
| **Android** | 🚧 `@MCPServer` annotations | 🚧 Intent-based discovery | **Active Development** |
| **iOS** | 🚧 `@MCPServer` annotations | ❌ Same-developer only | **Planned** |
| **Remote** | 🚧 HTTP/WebSocket | 🚧 URL-based | **Planned** |

## Quick Start

**Get started in 2 minutes:**

```kotlin
// LLM Apps: Add mmcp-client-android dependency
val mcpClient = MobileMCPClient()
mcpClient.startHttpServer() // Discover and access all local tools

// App Developers: Add mmcp-server-android dependency
@MCPServer class MyAppTools {
    @MCPTool suspend fun search(@MCPParam query: String) = myDatabase.search(query)
}
```

👉 **[Complete Android Integration Guide](android/README.md)**

## Key Features

- **Zero Boilerplate**: Annotations generate all necessary code
- **Mobile-Optimized**: HTTP-first design, respects platform constraints  
- **Future-Proof**: Supports stdio when mobile LLMs add it
- **Secure**: Permission-based access control
- **Fast**: Sub-100ms discovery and connection

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

## Use Cases

- **File Manager**: Expose file operations to LLMs
- **Contact App**: Let LLMs search and access contacts  
- **Calendar**: Enable LLMs to read/create events
- **Note-Taking**: Allow LLMs to search through notes
- **Photo Gallery**: Let LLMs analyze and search photos

## Documentation

| Resource | Description |
|----------|-------------|
| **[Android Integration Guide](android/README.md)** | Complete Android implementation guide |
| **[Architecture Deep-Dive](docs/mobile-mcp-architecture.md)** | Technical architecture and design decisions |
| **[Research Findings](research/)** | Mobile LLM integration research and insights |

## Development Status

🚧 **Early Development** - Core architecture designed, implementation in progress

- ✅ Research and architecture complete
- ✅ Android project structure established  
- 🔄 HTTP client library implementation
- ⏳ Annotation processor and code generation

## Contributing

We welcome contributions! This project aims to become the standard for mobile LLM-tool integration.

**Getting Started:** [Architecture docs](docs/mobile-mcp-architecture.md) → [Android guide](android/README.md) → Open issues

### License

Apache 2.0 - See [LICENSE](LICENSE) for details

## Why Mobile MCP Matters

As LLMs become more capable, they need access to real-world data and tools. Mobile devices are where most computing happens, but they're the most constrained environment. By solving mobile MCP integration, we unlock the full potential of AI assistants on the devices people use most.

**Join us in building the future of mobile AI integration.**
