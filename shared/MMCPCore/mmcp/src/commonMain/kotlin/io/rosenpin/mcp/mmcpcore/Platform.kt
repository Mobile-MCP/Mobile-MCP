package io.rosenpin.mmcp.mmcpcore

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform