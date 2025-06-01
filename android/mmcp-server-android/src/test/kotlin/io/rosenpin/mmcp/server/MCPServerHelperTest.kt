package io.rosenpin.mmcp.server

import org.junit.Test
import org.junit.Assert.*

/**
 * Test suite for MCPServerHelper utility class
 */
class MCPServerHelperTest {
    
    @Test
    fun testGenerateManifestXml() {
        val xml = MCPServerHelper.generateManifestXml(".MyMCPService", "com.example.app")
        
        assertTrue("Should contain service name", xml.contains(".MyMCPService"))
        assertTrue("Should contain MCP service action", xml.contains(McpConstants.ACTION_MCP_SERVICE))
        assertTrue("Should contain tool service action", xml.contains(McpConstants.ACTION_MCP_TOOL_SERVICE))
        assertTrue("Should contain resource service action", xml.contains(McpConstants.ACTION_MCP_RESOURCE_SERVICE))
        assertTrue("Should contain prompt service action", xml.contains(McpConstants.ACTION_MCP_PROMPT_SERVICE))
        assertTrue("Should contain discovery service action", xml.contains(McpConstants.ACTION_MCP_DISCOVERY_SERVICE))
        assertTrue("Should be exported", xml.contains("android:exported=\"true\""))
    }
    
    @Test
    fun testGenerateSampleServerClass() {
        val code = MCPServerHelper.generateSampleServerClass(
            packageName = "com.example.test",
            className = "TestMCPServer",
            serverId = "com.example.test.server",
            serverName = "Test Server",
            serverDescription = "A test server"
        )
        
        assertTrue("Should contain package declaration", code.contains("package com.example.test"))
        assertTrue("Should contain class name", code.contains("class TestMCPServer"))
        assertTrue("Should contain MCPServer annotation", code.contains("@MCPServer"))
        assertTrue("Should contain server ID", code.contains("com.example.test.server"))
        assertTrue("Should contain server name", code.contains("Test Server"))
        assertTrue("Should contain MCPServiceBase inheritance", code.contains(": MCPServiceBase()"))
        assertTrue("Should contain sample tool", code.contains("@MCPTool"))
        assertTrue("Should contain sample resource", code.contains("@MCPResource"))
        assertTrue("Should contain sample prompt", code.contains("@MCPPrompt"))
    }
    
    @Test
    fun testGenerateBuildGradle() {
        val gradle = MCPServerHelper.generateBuildGradle("2.0.0")
        
        assertTrue("Should contain MCP dependency", gradle.contains("mmcp-server-android:2.0.0"))
        assertTrue("Should contain dependencies block", gradle.contains("dependencies {"))
        assertTrue("Should contain core KTX", gradle.contains("androidx.core:core-ktx"))
        assertTrue("Should contain test dependencies", gradle.contains("junit:junit"))
    }
    
    @Test
    fun testGenerateProguardRules() {
        val rules = MCPServerHelper.generateProguardRules()
        
        assertTrue("Should keep MCP annotations", rules.contains("-keep @interface io.rosenpin.mmcp.server.annotations.**"))
        assertTrue("Should keep annotated classes", rules.contains("-keep @io.rosenpin.mmcp.server.annotations.MCPServer class *"))
        assertTrue("Should keep AIDL interfaces", rules.contains("-keep interface io.rosenpin.mmcp.server.IMcp**"))
        assertTrue("Should keep framework classes", rules.contains("-keep class io.rosenpin.mmcp.server.MCPServiceBase"))
    }
}