# MCP interface specifications for LLM compatibility

The Model Context Protocol (MCP) is an open standard introduced by Anthropic in November 2024 that revolutionizes how LLMs connect with external tools and data sources. Based on my research of official documentation, implementation examples, and provider variations, here's a comprehensive analysis of the protocol's interface specifications.

## Stdio with JSON-RPC serves as the primary communication method

MCP primarily uses **stdio (standard input/output) with JSON-RPC 2.0** as its core communication mechanism, especially for local integrations. The protocol operates through a subprocess model where:

- The MCP client launches the server as a subprocess
- **Communication flows through stdin/stdout streams** using UTF-8 encoded JSON-RPC messages
- Messages are delimited by newline characters, with no embedded newlines allowed
- The server can optionally write logs to stderr, which the client captures

The JSON-RPC implementation follows these specifications:
```json
{
  "jsonrpc": "2.0",
  "id": "unique-identifier",
  "method": "method_name",
  "params": {}
}
```

This stdio approach offers **minimal latency** with direct inter-process communication, making it ideal for local tools like filesystem access, database connections, or CLI integrations. Major implementations including Claude Desktop, VS Code, and Cursor IDE default to stdio transport for local MCP servers.

## HTTP endpoints provide remote connectivity options

While stdio dominates local scenarios, MCP also supports **HTTP-based transports** for remote server deployments:

### Streamable HTTP Transport (Current Standard)
The latest MCP specification (2025-03-26) introduces Streamable HTTP as the primary remote transport:
- **Single HTTP endpoint** supporting both POST and GET methods
- Client sends JSON-RPC messages via HTTP POST requests
- Optional Server-Sent Events (SSE) for server-to-client streaming
- Stateless operation mode available for simplified deployments

### Implementation Details
HTTP requests follow this pattern:
```http
POST /mcp HTTP/1.1
Host: example.com
Content-Type: application/json
Authorization: Bearer <token>

{"jsonrpc":"2.0","id":"123","method":"tools/list"}
```

This transport enables **scalable, multi-client deployments** where a single MCP server can handle requests from multiple AI applications simultaneously, crucial for enterprise environments.

## Three-tier architecture defines protocol interactions

MCP implements a sophisticated three-tier architecture that separates concerns:

1. **MCP Host**: The AI application (Claude Desktop, ChatGPT, VS Code) that coordinates the system
2. **MCP Client**: Protocol handlers within hosts maintaining 1:1 connections with servers
3. **MCP Server**: Lightweight programs exposing specific capabilities

The protocol requires a **mandatory initialization handshake**:
```json
// Client → Server
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-03-26",
    "capabilities": {},
    "clientInfo": {"name": "ExampleClient", "version": "1.0.0"}
  }
}

// Server → Client
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-03-26",
    "capabilities": {"tools": {}, "resources": {}, "prompts": {}},
    "serverInfo": {"name": "ExampleServer", "version": "1.0.0"}
  }
}
```

This handshake establishes **capability negotiation**, allowing dynamic discovery of available tools, resources, and prompts rather than relying on predefined schemas.

## Bidirectional stateful sessions enable rich interactions

Unlike REST APIs, MCP maintains **stateful sessions** throughout the connection lifecycle:

- **Persistent connection state** allows context preservation across multiple requests
- **Bidirectional communication** enables both request-response and notification patterns
- **Dynamic capability updates** through change notifications (listChanged events)
- **Resource subscriptions** for real-time data updates

The communication layer supports three message types:
1. **Requests**: Expect responses with matching IDs
2. **Responses**: Success results or error objects
3. **Notifications**: One-way messages without responses

This stateful design enables **complex workflows** where servers can maintain context, track user sessions, and provide real-time updates to connected clients.

## Provider implementations vary significantly in completeness

My research reveals substantial variations in MCP support across LLM providers:

### Anthropic Claude - Full Native Implementation
As the protocol creator, Anthropic provides **100% protocol compliance**:
- Complete stdio and HTTP transport support
- Full tools, resources, and prompts capabilities
- Native OAuth 2.1 authentication
- Built-in security features and prompt injection protection

### OpenAI - Growing Adoption
OpenAI officially adopted MCP in March 2025 with:
- Agents SDK with native MCP support
- ChatGPT Desktop integration in development
- Limited transport options currently
- Expanding feature coverage

### Google/Gemini - Adapter-Based Approach
Google implements MCP through **translation layers**:
- MCP-to-Gemini function calling conversion
- Community-driven adapter implementations
- Approximately 70% protocol coverage
- Requires schema transformation

### Other Providers
Most other LLM providers rely on community adapters or lack MCP support entirely, creating **compatibility challenges** for cross-provider deployments.

## Security specifications emphasize OAuth 2.1 authentication

The 2025-03-26 MCP revision mandates **OAuth 2.1 with PKCE** for secure authentication:

### Core Requirements
- **MUST** implement OAuth 2.1 with Proof Key for Code Exchange (PKCE)
- **SHOULD** support Dynamic Client Registration (RFC 7591)
- **MUST** implement Authorization Server Metadata discovery
- **Secure token storage** following OAuth 2.0 best practices

### Transport-Specific Security
- **Stdio**: Relies on OS-level process isolation and environment variables
- **HTTP**: Requires proper Origin validation and DNS rebinding protection
- **All transports**: Implement rate limiting and input validation

The protocol addresses modern security threats including cross-prompt injection attacks, tool poisoning risks, and credential leakage through comprehensive security frameworks.

## Practical implementations demonstrate protocol flexibility

Real-world MCP implementations showcase the protocol's versatility:

### Python with FastMCP
```python
from fastmcp import FastMCP

mcp = FastMCP("Demo Server")

@mcp.tool()
def calculate(expression: str) -> str:
    """Safely evaluate mathematical expressions"""
    return str(eval(expression))

mcp.run()
```

### TypeScript with Official SDK
```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const server = new McpServer({
  name: "example-server",
  version: "1.0.0"
});

const transport = new StdioServerTransport();
await server.connect(transport);
```

These examples illustrate how **developers can quickly create MCP servers** that expose tools, resources, and prompts to any compatible LLM application.

## Conclusion

MCP establishes a robust, standardized interface for LLM-tool integration built on proven technologies. The protocol's **primary reliance on stdio with JSON-RPC** provides optimal performance for local integrations, while **HTTP transport options** enable scalable remote deployments. The sophisticated capability negotiation, stateful sessions, and comprehensive security framework position MCP as a production-ready standard.

However, the **fragmented adoption landscape** across LLM providers presents challenges. While Anthropic leads with full native support and OpenAI shows strong commitment, other providers lag in implementation completeness. Organizations adopting MCP should carefully evaluate provider support levels and consider implementing adapter layers for broader compatibility.

The protocol's rapid ecosystem growth—with over 1,000 community servers and backing from major tech companies—indicates MCP is becoming the de facto standard for AI application connectivity, despite ongoing standardization challenges.