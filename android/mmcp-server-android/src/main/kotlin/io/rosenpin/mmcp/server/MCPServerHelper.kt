package io.rosenpin.mmcp.server

import io.rosenpin.mmcp.server.annotations.MCPServer
import io.rosenpin.mmcp.server.annotations.MCPTool
import io.rosenpin.mmcp.server.annotations.MCPParam

/**
 * Helper class providing utilities for MCP server development.
 * 
 * This class provides code generation and configuration helpers to make it easier
 * for 3rd party developers to create MCP servers.
 */
class MCPServerHelper {
    
    companion object {
        
        /**
         * Generate AndroidManifest.xml service declaration for an MCP server.
         * 
         * This generates the XML that developers need to add to their AndroidManifest.xml
         * to register their MCP server with the Android system.
         * 
         * @param serverClass The fully qualified class name of the MCP server
         * @param packageName The package name of the app (optional, for validation)
         * @return XML string to be added to AndroidManifest.xml
         */
        fun generateManifestXml(serverClass: String, packageName: String? = null): String {
            val className = if (serverClass.startsWith(".")) {
                // Relative class name
                serverClass
            } else if (packageName != null && serverClass.startsWith(packageName)) {
                // Full class name, convert to relative
                serverClass.removePrefix(packageName)
            } else {
                // Assume it's already in correct format
                serverClass
            }
            
            return """
                <!-- MCP Server Service Declaration -->
                <!-- Add this to your AndroidManifest.xml inside the <application> tag -->
                <service android:name="$className"
                    android:exported="true"
                    android:enabled="true">
                    
                    <!-- Main MCP Service -->
                    <intent-filter android:priority="100">
                        <action android:name="${McpConstants.ACTION_MCP_SERVICE}" />
                        <category android:name="android.intent.category.DEFAULT" />
                    </intent-filter>
                    
                    <!-- Tool Service -->
                    <intent-filter android:priority="100">
                        <action android:name="${McpConstants.ACTION_MCP_TOOL_SERVICE}" />
                        <category android:name="android.intent.category.DEFAULT" />
                    </intent-filter>
                    
                    <!-- Resource Service -->
                    <intent-filter android:priority="100">
                        <action android:name="${McpConstants.ACTION_MCP_RESOURCE_SERVICE}" />
                        <category android:name="android.intent.category.DEFAULT" />
                    </intent-filter>
                    
                    <!-- Prompt Service -->
                    <intent-filter android:priority="100">
                        <action android:name="${McpConstants.ACTION_MCP_PROMPT_SERVICE}" />
                        <category android:name="android.intent.category.DEFAULT" />
                    </intent-filter>
                    
                    <!-- Discovery Service -->
                    <intent-filter android:priority="100">
                        <action android:name="${McpConstants.ACTION_MCP_DISCOVERY_SERVICE}" />
                        <category android:name="android.intent.category.DEFAULT" />
                    </intent-filter>
                </service>
            """.trimIndent()
        }
        
        /**
         * Generate a sample MCP server implementation in Kotlin.
         * 
         * This creates a complete, working MCP server implementation that developers
         * can use as a starting point.
         * 
         * @param packageName The package name for the generated class
         * @param className The class name for the generated server
         * @param serverId Unique identifier for the MCP server
         * @param serverName Human-readable name for the server
         * @param serverDescription Description of what the server does
         * @return Complete Kotlin source code for an MCP server
         */
        fun generateSampleServerClass(
            packageName: String,
            className: String,
            serverId: String = "com.example.sample",
            serverName: String = "Sample MCP Server",
            serverDescription: String = "A sample MCP server implementation"
        ): String {
            return """
                package $packageName
                
                import io.rosenpin.mmcp.server.MCPServiceBase
                import io.rosenpin.mmcp.server.annotations.*
                import android.util.Log
                
                /**
                 * $serverDescription
                 * 
                 * This is a sample MCP server that demonstrates basic functionality.
                 * Extend this class and add your own @MCPTool, @MCPResource, and @MCPPrompt methods.
                 */
                @MCPServer(
                    id = "$serverId",
                    name = "$serverName",
                    description = "$serverDescription",
                    version = "1.0.0"
                )
                class $className : MCPServiceBase() {
                    
                    companion object {
                        private const val TAG = "$className"
                    }
                    
                    /**
                     * Example tool that greets a user by name.
                     * 
                     * This demonstrates how to create a simple tool with parameters.
                     */
                    @MCPTool(
                        id = "greet",
                        name = "Greet User",
                        description = "Generate a personalized greeting message",
                        parameters = ""${'"'}
                        {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string",
                                    "description": "Name of the person to greet"
                                },
                                "style": {
                                    "type": "string",
                                    "description": "Greeting style (formal, casual, friendly)",
                                    "enum": ["formal", "casual", "friendly"],
                                    "default": "friendly"
                                }
                            },
                            "required": ["name"]
                        }
                        ""${'"'}
                    )
                    fun greetUser(
                        @MCPParam("name") name: String,
                        @MCPParam("style") style: String = "friendly"
                    ): String {
                        Log.d(TAG, "Greeting ${'$'}name with ${'$'}style style")
                        
                        return when (style.lowercase()) {
                            "formal" -> "Good day, ${'$'}name. I hope you are well."
                            "casual" -> "Hey ${'$'}name! What's up?"
                            "friendly" -> "Hello ${'$'}name! Nice to meet you!"
                            else -> "Hi ${'$'}name!"
                        }
                    }
                    
                    /**
                     * Example tool that performs a simple calculation.
                     * 
                     * This demonstrates how to work with numeric parameters.
                     */
                    @MCPTool(
                        id = "calculate",
                        name = "Simple Calculator",
                        description = "Perform basic arithmetic operations",
                        parameters = ""${'"'}
                        {
                            "type": "object",
                            "properties": {
                                "operation": {
                                    "type": "string",
                                    "description": "The operation to perform",
                                    "enum": ["add", "subtract", "multiply", "divide"]
                                },
                                "a": {
                                    "type": "number",
                                    "description": "First number"
                                },
                                "b": {
                                    "type": "number",
                                    "description": "Second number"
                                }
                            },
                            "required": ["operation", "a", "b"]
                        }
                        ""${'"'}
                    )
                    fun calculate(
                        @MCPParam("operation") operation: String,
                        @MCPParam("a") a: Double,
                        @MCPParam("b") b: Double
                    ): String {
                        Log.d(TAG, "Calculating ${'$'}a ${'$'}operation ${'$'}b")
                        
                        val result = when (operation.lowercase()) {
                            "add" -> a + b
                            "subtract" -> a - b
                            "multiply" -> a * b
                            "divide" -> {
                                if (b == 0.0) {
                                    return "Error: Division by zero"
                                }
                                a / b
                            }
                            else -> return "Error: Unknown operation '${'$'}operation'"
                        }
                        
                        return "Result: ${'$'}result"
                    }
                    
                    /**
                     * Example resource that provides information about the server.
                     * 
                     * This demonstrates how to create a resource handler.
                     */
                    @MCPResource(
                        scheme = "server",
                        name = "Server Information",
                        description = "Provides information about this MCP server",
                        mimeType = "text/plain"
                    )
                    fun getServerInfo(uri: String): String {
                        Log.d(TAG, "Providing server info for URI: ${'$'}uri")
                        
                        return when {
                            uri.endsWith("/status") -> "Server is running normally"
                            uri.endsWith("/version") -> "Version 1.0.0"
                            uri.endsWith("/capabilities") -> "Tools: greet, calculate\nResources: server://\nPrompts: welcome"
                            else -> "Available endpoints:\n- server://status\n- server://version\n- server://capabilities"
                        }
                    }
                    
                    /**
                     * Example prompt that generates welcome messages.
                     * 
                     * This demonstrates how to create a prompt generator.
                     */
                    @MCPPrompt(
                        id = "welcome",
                        name = "Welcome Message",
                        description = "Generate a welcome message for new users",
                        parameters = ""${'"'}
                        {
                            "type": "object",
                            "properties": {
                                "appName": {
                                    "type": "string",
                                    "description": "Name of the application",
                                    "default": "MyApp"
                                },
                                "userName": {
                                    "type": "string",
                                    "description": "User's name (optional)"
                                }
                            }
                        }
                        ""${'"'}
                    )
                    fun generateWelcomePrompt(
                        @MCPParam("appName") appName: String = "MyApp",
                        @MCPParam("userName") userName: String? = null
                    ): String {
                        Log.d(TAG, "Generating welcome prompt for ${'$'}appName")
                        
                        val greeting = if (userName != null) {
                            "Welcome to ${'$'}appName, ${'$'}userName!"
                        } else {
                            "Welcome to ${'$'}appName!"
                        }
                        
                        return ""${'"'}
                            ${'$'}greeting
                            
                            This MCP server provides the following capabilities:
                            
                            🔧 Tools:
                            - greet: Generate personalized greetings
                            - calculate: Perform basic math operations
                            
                            📁 Resources:
                            - server://status - Check server status
                            - server://version - Get server version
                            - server://capabilities - List all capabilities
                            
                            💬 Prompts:
                            - welcome: Generate welcome messages
                            
                            Feel free to explore these features!
                        ""${'"'}.trimIndent()
                    }
                }
            """.trimIndent()
        }
        
        /**
         * Generate a sample build.gradle configuration for MCP server development.
         * 
         * @param mcpVersion The version of the MCP framework to use
         * @return build.gradle.kts content for MCP server development
         */
        fun generateBuildGradle(mcpVersion: String = "1.0.0"): String {
            return """
                dependencies {
                    // MCP Server Framework
                    implementation "io.rosenpin.mmcp:mmcp-server-android:$mcpVersion"
                    
                    // Android dependencies
                    implementation "androidx.core:core-ktx:1.12.0"
                    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"
                    
                    // Testing
                    testImplementation "junit:junit:4.13.2"
                    testImplementation "org.mockito:mockito-core:5.7.0"
                    androidTestImplementation "androidx.test.ext:junit:1.1.5"
                    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
                }
            """.trimIndent()
        }
        
        /**
         * Generate proguard rules for MCP server deployment.
         * 
         * @return ProGuard rules to preserve MCP annotations and framework classes
         */
        fun generateProguardRules(): String {
            return """
                # MCP Server Framework ProGuard Rules
                
                # Keep all MCP annotations
                -keep @interface io.rosenpin.mmcp.server.annotations.**
                
                # Keep classes annotated with MCP annotations
                -keep @io.rosenpin.mmcp.server.annotations.MCPServer class * {
                    *;
                }
                
                # Keep methods annotated with MCP annotations
                -keepclassmembers class * {
                    @io.rosenpin.mmcp.server.annotations.MCPTool *;
                    @io.rosenpin.mmcp.server.annotations.MCPResource *;
                    @io.rosenpin.mmcp.server.annotations.MCPPrompt *;
                }
                
                # Keep AIDL interfaces
                -keep interface io.rosenpin.mmcp.server.IMcp** { *; }
                -keep class io.rosenpin.mmcp.server.IMcp*${'$'}Stub { *; }
                
                # Keep MCP framework classes
                -keep class io.rosenpin.mmcp.server.MCPServiceBase { *; }
                -keep class io.rosenpin.mmcp.server.MCPMethodRegistry { *; }
                -keep class io.rosenpin.mmcp.server.annotations.** { *; }
                
                # Keep reflection-based method calls
                -keepclassmembers class * extends io.rosenpin.mmcp.server.MCPServiceBase {
                    public *;
                }
            """.trimIndent()
        }
    }
}