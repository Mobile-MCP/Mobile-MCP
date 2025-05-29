package io.rosenpin.mcp.mmcpcore

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform