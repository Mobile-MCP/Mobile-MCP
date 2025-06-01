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
        
        // Test Tools Section
        if (discoveredServers.contains("io.rosenpin.mcp.phonemcpserver")) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Phone MCP Server Tests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Connect button
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                try {
                                    val result = mcpClient.connectToServer("io.rosenpin.mcp.phonemcpserver")
                                    if (result.isSuccess) {
                                        connectionStatus = "Connected to phonemcpserver"
                                        testResults = testResults + "Connection: SUCCESS - Connected to server"
                                    } else {
                                        connectionStatus = "Connection failed: ${result.exceptionOrNull()?.message}"
                                        testResults = testResults + "Connection: FAILED - ${result.exceptionOrNull()?.message}"
                                    }
                                } catch (e: Exception) {
                                    connectionStatus = "Connection error: ${e.message}"
                                    testResults = testResults + "Connection: ERROR - ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect to Phone Server")
                    }
                    
                    val testButtons = listOf(
                        "Test Get Contacts" to "get_contacts",
                        "Test Search Contact" to "get_contact_by_name",
                        "Test Call History" to "get_call_history",
                        "Test Contact Resource" to "contact_resource"
                    )
                    
                    testButtons.forEach { (buttonText, testName) ->
                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    try {
                                        val result = when (testName) {
                                            "get_contacts" -> testGetContacts(mcpClient)
                                            "get_contact_by_name" -> testSearchContact(mcpClient)
                                            "get_call_history" -> testCallHistory(mcpClient)
                                            "contact_resource" -> testContactResource(mcpClient)
                                            else -> "Unknown test"
                                        }
                                        testResults = testResults + "$testName: $result"
                                    } catch (e: Exception) {
                                        testResults = testResults + "$testName: ERROR - ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(buttonText)
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

// Test Functions
private suspend fun testGetContacts(mcpClient: McpClient): String {
    val packageName = "io.rosenpin.mcp.phonemcpserver"
    return try {
        // First ensure we're connected
        val connectionResult = mcpClient.connectToServer(packageName)
        if (connectionResult.isFailure) {
            return "FAILED: Connection failed - ${connectionResult.exceptionOrNull()?.message}"
        }
        
        // Execute the get_contacts tool
        val result = mcpClient.executeTool(
            packageName = packageName,
            toolName = "get_contacts",
            parameters = mapOf("limit" to 10)
        )
        
        if (result.isSuccess) {
            "SUCCESS: ${result.getOrNull()}"
        } else {
            "FAILED: ${result.exceptionOrNull()?.message}"
        }
    } catch (e: Exception) {
        "FAILED: ${e.message}"
    }
}

private suspend fun testSearchContact(mcpClient: McpClient): String {
    val packageName = "io.rosenpin.mcp.phonemcpserver"
    return try {
        // Ensure we're connected
        if (!mcpClient.isConnectedTo(packageName)) {
            mcpClient.connectToServer(packageName)
        }
        
        // Execute the get_contact_by_name tool
        val result = mcpClient.executeTool(
            packageName = packageName,
            toolName = "get_contact_by_name",
            parameters = mapOf("name" to "John")
        )
        
        if (result.isSuccess) {
            "SUCCESS: ${result.getOrNull()}"
        } else {
            "FAILED: ${result.exceptionOrNull()?.message}"
        }
    } catch (e: Exception) {
        "FAILED: ${e.message}"
    }
}

private suspend fun testCallHistory(mcpClient: McpClient): String {
    val packageName = "io.rosenpin.mcp.phonemcpserver"
    return try {
        // Ensure we're connected
        if (!mcpClient.isConnectedTo(packageName)) {
            mcpClient.connectToServer(packageName)
        }
        
        // Execute the get_call_history tool
        val result = mcpClient.executeTool(
            packageName = packageName,
            toolName = "get_call_history",
            parameters = mapOf(
                "limit" to 5,
                "callType" to "all"
            )
        )
        
        if (result.isSuccess) {
            "SUCCESS: ${result.getOrNull()}"
        } else {
            "FAILED: ${result.exceptionOrNull()?.message}"
        }
    } catch (e: Exception) {
        "FAILED: ${e.message}"
    }
}

private suspend fun testContactResource(mcpClient: McpClient): String {
    val packageName = "io.rosenpin.mcp.phonemcpserver"
    return try {
        // Ensure we're connected
        if (!mcpClient.isConnectedTo(packageName)) {
            mcpClient.connectToServer(packageName)
        }
        
        // Read a contact resource
        val result = mcpClient.readResource(
            packageName = packageName,
            resourceUri = "contact://1"
        )
        
        if (result.isSuccess) {
            "SUCCESS: ${result.getOrNull()}"
        } else {
            "FAILED: ${result.exceptionOrNull()?.message}"
        }
    } catch (e: Exception) {
        "FAILED: ${e.message}"
    }
}

@Preview(showBackground = true)
@Composable
fun MCPClientTestScreenPreview() {
    MMCPExampleTheme {
        // Preview with mock data
    }
}