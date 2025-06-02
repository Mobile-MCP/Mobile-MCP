package io.rosenpin.mcp.phonemcpserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.rosenpin.mcp.phonemcpserver.ui.theme.MMCPAndroidTheme

class MainActivity : ComponentActivity() {
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG
    )
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // All permissions granted - MCP server is ready
        } else {
            // Some permissions denied - explain why they're needed
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MMCPAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MCPServerScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermissions = { requestPermissions() }
                    )
                }
            }
        }
        
        // Request permissions on first launch
        requestPermissions()
    }
    
    private fun requestPermissions() {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

@Composable
fun MCPServerScreen(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val requiredPermissions = listOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG
    )
    
    val permissionStatus = requiredPermissions.map { permission ->
        permission to (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    
    val allPermissionsGranted = permissionStatus.all { it.second }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Phone & Contacts MCP Server",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "This app provides MCP (Model Context Protocol) server functionality for phone and contacts access.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Server Status:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (allPermissionsGranted) "Ready" else "Waiting for Permissions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (allPermissionsGranted) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
                
                if (allPermissionsGranted) {
                    Text(
                        text = "✅ All permissions granted. MCP clients can now discover and connect to this server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "⚠️ Some permissions are missing. Please grant all permissions for full functionality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Permissions Section
        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(permissionStatus) { (permission, granted) ->
                PermissionItem(
                    permission = permission,
                    granted = granted
                )
            }
        }
        
        if (!allPermissionsGranted) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Capabilities Section
        Text(
            text = "Available MCP Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        val capabilities = listOf(
            "📞 Make Phone Calls" to "Initiate calls to phone numbers",
            "👥 Access Contacts" to "List and search device contacts",
            "📋 Get Contact Details" to "Retrieve phone numbers by name",
            "📊 Call History" to "Access call logs and history"
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(capabilities) { (title, description) ->
                CapabilityItem(title = title, description = description)
            }
        }
    }
}

@Composable
fun PermissionItem(permission: String, granted: Boolean) {
    val (title, description) = when (permission) {
        Manifest.permission.READ_CONTACTS -> "Contacts" to "Read device contacts and phone numbers"
        Manifest.permission.CALL_PHONE -> "Phone Calls" to "Make phone calls on behalf of MCP clients"
        Manifest.permission.READ_CALL_LOG -> "Call History" to "Access call logs and history"
        else -> permission to "Unknown permission"
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = if (granted) "✅" else "❌",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun CapabilityItem(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MCPServerScreenPreview() {
    MMCPAndroidTheme {
        MCPServerScreen()
    }
}