package io.rosenpin.mcp.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.rosenpin.mcp.example.ui.theme.MMCPExampleTheme
import io.rosenpin.mmcp.client.McpClient
import kotlinx.coroutines.launch
import android.util.Log

private const val TAG = "MCPExampleApp"

class MainActivity : ComponentActivity() {
    
    private lateinit var mcpClient: McpClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize MCP client
        mcpClient = McpClient(this)
        
        setContent {
            MMCPExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MCPClientTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        mcpClient = mcpClient,
                        lifecycleScope = lifecycleScope
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mcpClient.cleanup()
    }
}

@Composable
fun MCPClientTestScreen(
    modifier: Modifier = Modifier,
    mcpClient: McpClient,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    var discoveredServers by remember { mutableStateOf<List<String>>(emptyList()) }
    var connectionStatus by remember { mutableStateOf("Not connected") }
    var testResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "MCP Client Test App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "Test client for connecting to MCP servers and executing tools",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Discovery Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Server Discovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = {
                        isLoading = true
                        lifecycleScope.launch {
                            try {
                                val result = mcpClient.startDiscovery()
                                kotlinx.coroutines.delay(2000) // Give discovery time
                                
                                if (result.isSuccess) {
                                    val servers = mcpClient.discoveredServers.value
                                    discoveredServers = servers.map { it.packageName }
                                    connectionStatus = "Discovery completed - Found ${servers.size} servers"
                                    Log.i(TAG, "Discovery completed - Found servers: ${discoveredServers.joinToString(", ")}")
                                } else {
                                    connectionStatus = "Discovery failed: ${result.exceptionOrNull()?.message}"
                                }
                            } catch (e: Exception) {
                                connectionStatus = "Discovery failed: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Discover MCP Servers")
                }
                
                if (discoveredServers.isNotEmpty()) {
                    Text("Found ${discoveredServers.size} servers:")
                    discoveredServers.forEach { server ->
                        Text(
                            text = "• $server",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        
        // Connection Status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Dynamic Server Testing Section
        if (discoveredServers.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dynamic MCP Server Testing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    discoveredServers.forEach { serverPackage ->
                        val serverInfo = mcpClient.discoveredServers.value.find { it.packageName == serverPackage }
                        // Use the server's package name as display name for now
                        // serverInfo?.serverInfo might be null if not yet connected
                        val serverName = serverPackage.substringAfterLast('.')
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = serverName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = "Package: $serverPackage",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                // Connect/Test buttons for this server
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch {
                                                testServerConnection(mcpClient, serverPackage) { result ->
                                                    Log.i(TAG, "TEST RESULT: $result")
                                                    testResults = testResults + result
                                                    connectionStatus = if (result.contains("SUCCESS")) {
                                                        "Connected to $serverName"
                                                    } else {
                                                        "Connection failed for $serverName"
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Connect")
                                    }
                                    
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch {
                                                testServerCapabilities(mcpClient, serverPackage) { result ->
                                                    Log.i(TAG, "TEST RESULT: $result")
                                                    testResults = testResults + result
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Test All")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Test Results
        if (testResults.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Test Results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { testResults = emptyList() }) {
                            Text("Clear")
                        }
                    }
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(testResults.reversed()) { result ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dynamic Test Functions
private suspend fun testServerConnection(
    mcpClient: McpClient, 
    packageName: String,
    onResult: (String) -> Unit
) {
    try {
        Log.d(TAG, "Testing connection to server: $packageName")
        val connectionResult = mcpClient.connectToServer(packageName)
        if (connectionResult.isSuccess) {
            val message = "Connection to $packageName: SUCCESS"
            Log.i(TAG, message)
            onResult(message)
        } else {
            val message = "Connection to $packageName: FAILED - ${connectionResult.exceptionOrNull()?.message}"
            Log.e(TAG, message)
            onResult(message)
        }
    } catch (e: Exception) {
        val message = "Connection to $packageName: ERROR - ${e.message}"
        Log.e(TAG, message, e)
        onResult(message)
    }
}

private suspend fun testServerCapabilities(
    mcpClient: McpClient,
    packageName: String,
    onResult: (String) -> Unit
) {
    try {
        Log.d(TAG, "Testing capabilities for server: $packageName")
        
        // Ensure we're connected first
        if (!mcpClient.isConnectedTo(packageName)) {
            Log.d(TAG, "Not connected to $packageName, connecting first...")
            val connectionResult = mcpClient.connectToServer(packageName)
            if (connectionResult.isFailure) {
                val message = "$packageName: Connection failed before testing"
                Log.e(TAG, message)
                onResult(message)
                return
            }
        }
        
        // Test tools if available
        val connection = mcpClient.getConnection(packageName)
        Log.d(TAG, "Connection info - hasTools: ${connection?.hasTools}, hasResources: ${connection?.hasResources}, hasPrompts: ${connection?.hasPrompts}")
        
        if (connection?.hasTools == true) {
            Log.d(TAG, "Testing tools for $packageName...")
            val toolsResult = mcpClient.listTools(packageName)
            if (toolsResult.isSuccess) {
                val tools = toolsResult.getOrNull() ?: emptyList()
                val message = "$packageName Tools: Found ${tools.size} tools - ${tools.joinToString(", ")}"
                Log.i(TAG, message)
                onResult(message)
                
                // Test the first tool with minimal parameters
                if (tools.isNotEmpty()) {
                    val firstTool = tools.first()
                    Log.d(TAG, "Testing first tool: $firstTool")
                    val testResult = mcpClient.executeTool(
                        packageName = packageName,
                        toolName = firstTool,
                        parameters = emptyMap() // Start with no parameters
                    )
                    
                    if (testResult.isSuccess) {
                        val successMsg = "$packageName Tool '$firstTool': SUCCESS - ${testResult.getOrNull()}"
                        Log.i(TAG, successMsg)
                        onResult(successMsg)
                    } else {
                        val errorMsg = "$packageName Tool '$firstTool': FAILED - ${testResult.exceptionOrNull()?.message}"
                        Log.e(TAG, errorMsg)
                        onResult(errorMsg)
                    }
                }
            } else {
                val errorMsg = "$packageName Tools: Failed to list - ${toolsResult.exceptionOrNull()?.message}"
                Log.e(TAG, errorMsg)
                onResult(errorMsg)
            }
        }
        
        // Test resources if available
        if (connection?.hasResources == true) {
            Log.d(TAG, "Testing resources for $packageName...")
            val resourcesResult = mcpClient.listResources(packageName)
            if (resourcesResult.isSuccess) {
                val resources = resourcesResult.getOrNull() ?: emptyList()
                val message = "$packageName Resources: Found ${resources.size} resources - ${resources.joinToString(", ")}"
                Log.i(TAG, message)
                onResult(message)
                
                // Test the first resource
                if (resources.isNotEmpty()) {
                    val firstResource = resources.first()
                    Log.d(TAG, "Testing first resource: $firstResource")
                    val testResult = mcpClient.readResource(
                        packageName = packageName,
                        resourceUri = firstResource
                    )
                    
                    if (testResult.isSuccess) {
                        val successMsg = "$packageName Resource '$firstResource': SUCCESS - ${testResult.getOrNull()}"
                        Log.i(TAG, successMsg)
                        onResult(successMsg)
                    } else {
                        val errorMsg = "$packageName Resource '$firstResource': FAILED - ${testResult.exceptionOrNull()?.message}"
                        Log.e(TAG, errorMsg)
                        onResult(errorMsg)
                    }
                }
            } else {
                val errorMsg = "$packageName Resources: Failed to list - ${resourcesResult.exceptionOrNull()?.message}"
                Log.e(TAG, errorMsg)
                onResult(errorMsg)
            }
        }
        
        // Test prompts if available
        if (connection?.hasPrompts == true) {
            Log.d(TAG, "Testing prompts for $packageName...")
            val promptsResult = mcpClient.listPrompts(packageName)
            if (promptsResult.isSuccess) {
                val prompts = promptsResult.getOrNull() ?: emptyList()
                val message = "$packageName Prompts: Found ${prompts.size} prompts - ${prompts.joinToString(", ")}"
                Log.i(TAG, message)
                onResult(message)
                
                // Test the first prompt
                if (prompts.isNotEmpty()) {
                    val firstPrompt = prompts.first()
                    Log.d(TAG, "Testing first prompt: $firstPrompt")
                    val testResult = mcpClient.getPrompt(
                        packageName = packageName,
                        promptName = firstPrompt,
                        parameters = emptyMap()
                    )
                    
                    if (testResult.isSuccess) {
                        val successMsg = "$packageName Prompt '$firstPrompt': SUCCESS - ${testResult.getOrNull()}"
                        Log.i(TAG, successMsg)
                        onResult(successMsg)
                    } else {
                        val errorMsg = "$packageName Prompt '$firstPrompt': FAILED - ${testResult.exceptionOrNull()?.message}"
                        Log.e(TAG, errorMsg)
                        onResult(errorMsg)
                    }
                }
            } else {
                val errorMsg = "$packageName Prompts: Failed to list - ${promptsResult.exceptionOrNull()?.message}"
                Log.e(TAG, errorMsg)
                onResult(errorMsg)
            }
        }
        
        if (connection?.hasTools != true && connection?.hasResources != true && connection?.hasPrompts != true) {
            val message = "$packageName: No capabilities available"
            Log.w(TAG, message)
            onResult(message)
        }
        
    } catch (e: Exception) {
        val errorMsg = "$packageName Test: ERROR - ${e.message}"
        Log.e(TAG, errorMsg, e)
        onResult(errorMsg)
    }
}

@Preview(showBackground = true)
@Composable
fun MCPClientTestScreenPreview() {
    MMCPExampleTheme {
        // Preview with mock data
    }
}