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
import com.google.gson.Gson
import androidx.compose.material3.TextField
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer

private const val TAG = "MCPExampleApp"

// Data class to store discovered capabilities for each server
data class ServerCapabilities(
    val tools: List<ToolInfo> = emptyList(),
    val resources: List<String> = emptyList(),
    val prompts: List<PromptInfo> = emptyList()
)

data class ToolInfo(
    val id: String,
    val name: String = "",
    val description: String = "",
    val parametersSchema: String = "{}"
)

data class PromptInfo(
    val id: String,
    val name: String = "",
    val description: String = "",
    val parametersSchema: String = "{}"
)

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
    
    // Store discovered capabilities for each server
    var serverCapabilities by remember { mutableStateOf<Map<String, ServerCapabilities>>(emptyMap()) }
    
    // Dialog state for parameter input
    var showingToolDialog by remember { mutableStateOf<ToolInfo?>(null) }
    var showingPromptDialog by remember { mutableStateOf<PromptInfo?>(null) }
    var showingDialogForServer by remember { mutableStateOf("") }
    
    // Result dialog state
    var showingResultDialog by remember { mutableStateOf(false) }
    var resultDialogTitle by remember { mutableStateOf("") }
    var resultDialogContent by remember { mutableStateOf("") }
    var resultDialogIsError by remember { mutableStateOf(false) }
    
    val gson = remember { Gson() }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
                                                // First ensure connection
                                                if (!mcpClient.isConnectedTo(serverPackage)) {
                                                    val connectionResult = mcpClient.connectToServer(serverPackage)
                                                    if (connectionResult.isFailure) {
                                                        testResults = testResults + "$serverPackage: Connection failed"
                                                        return@launch
                                                    }
                                                }
                                                
                                                // Discover capabilities
                                                discoverServerCapabilities(mcpClient, serverPackage) { capabilities ->
                                                    serverCapabilities = serverCapabilities + (serverPackage to capabilities)
                                                    val summary = buildString {
                                                        append("$serverPackage: ")
                                                        append("Tools: ${capabilities.tools.size}, ")
                                                        append("Resources: ${capabilities.resources.size}, ")
                                                        append("Prompts: ${capabilities.prompts.size}")
                                                    }
                                                    Log.i(TAG, "Discovered capabilities - $summary")
                                                    testResults = testResults + summary
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Discover")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Dynamic Capability Buttons Section
        serverCapabilities.forEach { (serverPackage, capabilities) ->
            if (capabilities.tools.isNotEmpty() || capabilities.resources.isNotEmpty() || capabilities.prompts.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val serverName = serverPackage.substringAfterLast('.')
                        Text(
                            text = "$serverName Capabilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Tools section
                        if (capabilities.tools.isNotEmpty()) {
                            Text(
                                text = "Tools:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            capabilities.tools.forEach { tool ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = tool.name.ifEmpty { tool.id },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (tool.description.isNotEmpty()) {
                                            Text(
                                                text = tool.description,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        
                                        // Parse schema to show parameters
                                        val schema = try {
                                            gson.fromJson(tool.parametersSchema, Map::class.java)
                                        } catch (e: Exception) {
                                            null
                                        }
                                        
                                        val requiredParams = (schema?.get("required") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                        val properties = schema?.get("properties") as? Map<*, *>
                                        val allParams = properties?.keys?.filterIsInstance<String>() ?: emptyList()
                                        
                                        if (allParams.isNotEmpty() && properties != null) {
                                            Text(
                                                text = "Parameters:",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            allParams.forEach { param ->
                                                val paramInfo = properties[param] as? Map<*, *>
                                                val paramType = paramInfo?.get("type") as? String ?: "string"
                                                val paramDesc = paramInfo?.get("description") as? String ?: param
                                                val isRequired = requiredParams.contains(param)
                                                val defaultValue = paramInfo?.get("default")
                                                Text(
                                                    text = "• $param ($paramType)${if (isRequired) " *" else ""}: $paramDesc${if (defaultValue != null) " (default: $defaultValue)" else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                // Show parameter input dialog
                                                showingToolDialog = tool
                                                showingDialogForServer = serverPackage
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Execute")
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Resources section
                        if (capabilities.resources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Resources:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            capabilities.resources.forEach { resource ->
                                OutlinedButton(
                                    onClick = {
                                        lifecycleScope.launch {
                                            readResourceWithUI(
                                                mcpClient, 
                                                serverPackage, 
                                                resource,
                                                onResult = { result ->
                                                    testResults = testResults + result
                                                },
                                                onShowDialog = { title, content, isError ->
                                                    resultDialogTitle = title
                                                    resultDialogContent = content
                                                    resultDialogIsError = isError
                                                    showingResultDialog = true
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Read: $resource")
                                }
                            }
                        }
                        
                        // Prompts section
                        if (capabilities.prompts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Prompts:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            capabilities.prompts.forEach { prompt ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = prompt.name.ifEmpty { prompt.id },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (prompt.description.isNotEmpty()) {
                                            Text(
                                                text = prompt.description,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                // Show parameter input dialog for prompts
                                                showingPromptDialog = prompt
                                                showingDialogForServer = serverPackage
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Get prompt")
                                        }
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
    
    // Parameter input dialog for tools
    showingToolDialog?.let { tool ->
        ParameterInputDialog(
            title = tool.name.ifEmpty { tool.id },
            parametersSchema = tool.parametersSchema,
            gson = gson,
            onDismiss = { showingToolDialog = null },
            onConfirm = { params ->
                lifecycleScope.launch {
                    executeToolWithUI(
                        mcpClient, 
                        showingDialogForServer, 
                        tool, 
                        params,
                        onResult = { result ->
                            testResults = testResults + result
                        },
                        onShowDialog = { title, content, isError ->
                            resultDialogTitle = title
                            resultDialogContent = content
                            resultDialogIsError = isError
                            showingResultDialog = true
                        }
                    )
                }
                showingToolDialog = null
            }
        )
    }
    
    // Parameter input dialog for prompts
    showingPromptDialog?.let { prompt ->
        ParameterInputDialog(
            title = prompt.name.ifEmpty { prompt.id },
            parametersSchema = prompt.parametersSchema,
            gson = gson,
            onDismiss = { showingPromptDialog = null },
            onConfirm = { params ->
                lifecycleScope.launch {
                    getPromptWithUI(
                        mcpClient, 
                        showingDialogForServer, 
                        prompt, 
                        params,
                        onResult = { result ->
                            testResults = testResults + result
                        },
                        onShowDialog = { title, content, isError ->
                            resultDialogTitle = title
                            resultDialogContent = content
                            resultDialogIsError = isError
                            showingResultDialog = true
                        }
                    )
                }
                showingPromptDialog = null
            }
        )
    }
    
    // Result dialog to show MCP call results
    if (showingResultDialog) {
        ResultDialog(
            title = resultDialogTitle,
            content = resultDialogContent,
            isError = resultDialogIsError,
            onDismiss = { showingResultDialog = false }
        )
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

// Helper function to discover server capabilities
private suspend fun discoverServerCapabilities(
    mcpClient: McpClient,
    packageName: String,
    onResult: (ServerCapabilities) -> Unit
) {
    val connection = mcpClient.getConnection(packageName) ?: return
    
    val toolsList = mutableListOf<ToolInfo>()
    val resourcesList = mutableListOf<String>()
    val promptsList = mutableListOf<PromptInfo>()
    
    // Discover tools with their info
    if (connection.hasTools) {
        val toolsResult = mcpClient.listTools(packageName)
        if (toolsResult.isSuccess) {
            val toolIds = toolsResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Discovered ${toolIds.size} tools: ${toolIds.joinToString(", ")}")
            
            // Get detailed info for each tool
            toolIds.forEach { toolId ->
                try {
                    val toolInfoResult = mcpClient.getToolInfo(packageName, toolId)
                    if (toolInfoResult.isSuccess) {
                        val toolInfo = toolInfoResult.getOrNull()
                        if (!toolInfo.isNullOrEmpty()) {
                            // Parse format: "name:description:parametersSchema"
                            val parts = toolInfo.split(":", limit = 3)
                            toolsList.add(ToolInfo(
                                id = toolId,
                                name = parts.getOrNull(0) ?: toolId,
                                description = parts.getOrNull(1) ?: "",
                                parametersSchema = parts.getOrNull(2) ?: "{}"
                            ))
                            Log.d(TAG, "Tool '$toolId' schema: ${parts.getOrNull(2)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get info for tool '$toolId'", e)
                    // Add basic info anyway
                    toolsList.add(ToolInfo(id = toolId))
                }
            }
        }
    }
    
    // Discover resources
    if (connection.hasResources) {
        val resourcesResult = mcpClient.listResources(packageName)
        if (resourcesResult.isSuccess) {
            resourcesList.addAll(resourcesResult.getOrNull() ?: emptyList())
            Log.d(TAG, "Discovered ${resourcesList.size} resources: ${resourcesList.joinToString(", ")}")
        }
    }
    
    // Discover prompts with their info
    if (connection.hasPrompts) {
        val promptsResult = mcpClient.listPrompts(packageName)
        if (promptsResult.isSuccess) {
            val promptIds = promptsResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Discovered ${promptIds.size} prompts: ${promptIds.joinToString(", ")}")
            
            // Get detailed info for each prompt
            promptIds.forEach { promptId ->
                try {
                    val promptInfoResult = mcpClient.getPromptInfo(packageName, promptId)
                    if (promptInfoResult.isSuccess) {
                        val promptInfo = promptInfoResult.getOrNull()
                        if (!promptInfo.isNullOrEmpty()) {
                            // Parse format: "name:description:parametersSchema"
                            val parts = promptInfo.split(":", limit = 3)
                            promptsList.add(PromptInfo(
                                id = promptId,
                                name = parts.getOrNull(0) ?: promptId,
                                description = parts.getOrNull(1) ?: "",
                                parametersSchema = parts.getOrNull(2) ?: "{}"
                            ))
                            Log.d(TAG, "Prompt '$promptId' schema: ${parts.getOrNull(2)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get info for prompt '$promptId'", e)
                    // Add basic info anyway
                    promptsList.add(PromptInfo(id = promptId))
                }
            }
        }
    }
    
    onResult(ServerCapabilities(
        tools = toolsList,
        resources = resourcesList,
        prompts = promptsList
    ))
}

// Helper function to execute tool with UI feedback
private suspend fun executeToolWithUI(
    mcpClient: McpClient,
    packageName: String,
    tool: ToolInfo,
    parameters: Map<String, Any> = emptyMap(),
    onResult: (String) -> Unit,
    onShowDialog: (title: String, content: String, isError: Boolean) -> Unit
) {
    try {
        Log.d(TAG, "Executing tool '${tool.id}' on $packageName with parameters: $parameters")
        val result = mcpClient.executeTool(packageName, tool.id, parameters)
        
        if (result.isSuccess) {
            val resultContent = result.getOrNull() ?: "No result returned"
            val message = "Tool '${tool.name.ifEmpty { tool.id }}': $resultContent"
            Log.i(TAG, message)
            onResult(message)
            
            // Show success dialog
            onShowDialog(
                "Tool Result: ${tool.name.ifEmpty { tool.id }}",
                resultContent,
                false
            )
        } else {
            val error = result.exceptionOrNull()
            val errorMessage = error?.message ?: "Unknown error"
            val message = "Tool '${tool.name.ifEmpty { tool.id }}' failed: $errorMessage"
            Log.e(TAG, message, error)
            onResult(message)
            
            // Show error dialog
            onShowDialog(
                "Tool Failed: ${tool.name.ifEmpty { tool.id }}",
                errorMessage,
                true
            )
        }
    } catch (e: Exception) {
        val message = "Tool '${tool.name.ifEmpty { tool.id }}' error: ${e.message}"
        Log.e(TAG, message, e)
        onResult(message)
        
        // Show error dialog
        onShowDialog(
            "Tool Error: ${tool.name.ifEmpty { tool.id }}",
            e.message ?: "An unexpected error occurred",
            true
        )
    }
}

// Helper function to read resource with UI feedback
private suspend fun readResourceWithUI(
    mcpClient: McpClient,
    packageName: String,
    resourceUri: String,
    onResult: (String) -> Unit,
    onShowDialog: (title: String, content: String, isError: Boolean) -> Unit
) {
    try {
        Log.d(TAG, "Reading resource '$resourceUri' from $packageName")
        val result = mcpClient.readResource(packageName, resourceUri)
        
        if (result.isSuccess) {
            val content = result.getOrNull() ?: ""
            val preview = if (content.length > 100) content.take(100) + "..." else content
            val message = "Resource '$resourceUri': $preview"
            Log.i(TAG, "Resource read success: $resourceUri")
            onResult(message)
            
            // Show full content in dialog
            onShowDialog(
                "Resource: $resourceUri",
                content,
                false
            )
        } else {
            val error = result.exceptionOrNull()
            val errorMessage = error?.message ?: "Unknown error"
            val message = "Resource '$resourceUri' failed: $errorMessage"
            Log.e(TAG, message, error)
            onResult(message)
            
            // Show error dialog
            onShowDialog(
                "Resource Failed: $resourceUri",
                errorMessage,
                true
            )
        }
    } catch (e: Exception) {
        val message = "Resource '$resourceUri' error: ${e.message}"
        Log.e(TAG, message, e)
        onResult(message)
        
        // Show error dialog
        onShowDialog(
            "Resource Error: $resourceUri",
            e.message ?: "An unexpected error occurred",
            true
        )
    }
}

// Helper function to get prompt with UI feedback
private suspend fun getPromptWithUI(
    mcpClient: McpClient,
    packageName: String,
    prompt: PromptInfo,
    parameters: Map<String, Any> = emptyMap(),
    onResult: (String) -> Unit,
    onShowDialog: (title: String, content: String, isError: Boolean) -> Unit
) {
    try {
        Log.d(TAG, "Getting prompt '${prompt.id}' from $packageName")
        val result = mcpClient.getPrompt(packageName, prompt.id, parameters)
        
        if (result.isSuccess) {
            val content = result.getOrNull() ?: ""
            val preview = if (content.length > 100) content.take(100) + "..." else content
            val message = "Prompt '${prompt.name.ifEmpty { prompt.id }}': $preview"
            Log.i(TAG, "Prompt retrieved: ${prompt.id}")
            onResult(message)
            
            // Show full prompt in dialog
            onShowDialog(
                "Prompt: ${prompt.name.ifEmpty { prompt.id }}",
                content,
                false
            )
        } else {
            val error = result.exceptionOrNull()
            val errorMessage = error?.message ?: "Unknown error"
            val message = "Prompt '${prompt.name.ifEmpty { prompt.id }}' failed: $errorMessage"
            Log.e(TAG, message, error)
            onResult(message)
            
            // Show error dialog
            onShowDialog(
                "Prompt Failed: ${prompt.name.ifEmpty { prompt.id }}",
                errorMessage,
                true
            )
        }
    } catch (e: Exception) {
        val message = "Prompt '${prompt.name.ifEmpty { prompt.id }}' error: ${e.message}"
        Log.e(TAG, message, e)
        onResult(message)
        
        // Show error dialog
        onShowDialog(
            "Prompt Error: ${prompt.name.ifEmpty { prompt.id }}",
            e.message ?: "An unexpected error occurred",
            true
        )
    }
}

@Composable
fun ParameterInputDialog(
    title: String,
    parametersSchema: String,
    gson: Gson,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Any>) -> Unit
) {
    val schema = try {
        gson.fromJson(parametersSchema, Map::class.java)
    } catch (e: Exception) {
        null
    }
    
    val properties = schema?.get("properties") as? Map<*, *> ?: emptyMap<String, Any>()
    val requiredParams = (schema?.get("required") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    
    // State for each parameter
    val parameterValues = remember { mutableStateMapOf<String, String>() }
    
    // Initialize with defaults
    LaunchedEffect(properties) {
        properties.forEach { (key, value) ->
            val paramInfo = value as? Map<*, *>
            val defaultValue = paramInfo?.get("default")
            if (defaultValue != null && key is String) {
                parameterValues[key] = defaultValue.toString()
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                HorizontalDivider()
                
                // Input fields for each parameter
                properties.forEach { (paramName, paramValue) ->
                    if (paramName is String) {
                        val paramInfo = paramValue as? Map<*, *>
                        val paramType = paramInfo?.get("type") as? String ?: "string"
                        val paramDesc = paramInfo?.get("description") as? String ?: paramName
                        val isRequired = requiredParams.contains(paramName)
                        val enumValues = (paramInfo?.get("enum") as? List<*>)?.filterIsInstance<String>()
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$paramName${if (isRequired) " *" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = paramDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (enumValues != null) {
                                // Dropdown for enum values
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(parameterValues[paramName] ?: enumValues.firstOrNull() ?: "Select")
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        enumValues.forEach { value ->
                                            DropdownMenuItem(
                                                text = { Text(value) },
                                                onClick = {
                                                    parameterValues[paramName] = value
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Text field for other types
                                TextField(
                                    value = parameterValues[paramName] ?: "",
                                    onValueChange = { parameterValues[paramName] = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { 
                                        Text(
                                            when (paramType) {
                                                "integer", "number" -> "Enter number"
                                                "boolean" -> "true or false"
                                                else -> "Enter $paramType"
                                            }
                                        )
                                    },
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider()
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            // Convert parameter values to appropriate types
                            val params = mutableMapOf<String, Any>()
                            properties.forEach { (key, value) ->
                                if (key is String) {
                                    val paramInfo = value as? Map<*, *>
                                    val paramType = paramInfo?.get("type") as? String ?: "string"
                                    val paramValue = parameterValues[key]
                                    
                                    if (!paramValue.isNullOrEmpty()) {
                                        params[key] = when (paramType) {
                                            "integer" -> paramValue.toIntOrNull() ?: 0
                                            "number" -> {
                                                // Check if it's actually an integer value
                                                val doubleValue = paramValue.toDoubleOrNull() ?: 0.0
                                                if (doubleValue % 1.0 == 0.0) {
                                                    doubleValue.toInt()
                                                } else {
                                                    doubleValue
                                                }
                                            }
                                            "boolean" -> paramValue.toBoolean()
                                            else -> paramValue
                                        }
                                    } else if (requiredParams.contains(key)) {
                                        // Use default for required params if empty
                                        params[key] = when (paramType) {
                                            "integer" -> 0
                                            "number" -> 0
                                            "boolean" -> false
                                            else -> ""
                                        }
                                    }
                                }
                            }
                            onConfirm(params)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Execute")
                    }
                }
            }
        }
    }
}

@Composable
fun ResultDialog(
    title: String,
    content: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isError) "❌" else "✅",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                
                HorizontalDivider()
                
                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    item {
                        // Format JSON if possible
                        val formattedContent = try {
                            if (content.trimStart().startsWith("{") || content.trimStart().startsWith("[")) {
                                // Try to parse and pretty-print JSON
                                val gson = com.google.gson.GsonBuilder()
                                    .setPrettyPrinting()
                                    .create()
                                val jsonElement = gson.fromJson(content, com.google.gson.JsonElement::class.java)
                                gson.toJson(jsonElement)
                            } else {
                                content
                            }
                        } catch (e: Exception) {
                            content
                        }
                        
                        SelectionContainer {
                            Text(
                                text = formattedContent,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = if (isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = if (isError) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MCPClientTestScreenPreview() {
    MMCPExampleTheme {
        // Preview with mock data
    }
}