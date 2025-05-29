package io.rosenpin.mcp.server

import android.os.Parcel
import android.os.Parcelable

/**
 * Data class representing MCP server information
 * Contains metadata about an MCP server for discovery and connection
 */
data class McpServerInfo(
    val packageName: String,
    val serviceName: String,
    val serverName: String,
    val version: String,
    val description: String,
    val capabilities: List<String>,
    val protocolVersion: String = "2024-11-05"
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        parcel.readString() ?: "2024-11-05"
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeString(serviceName)
        parcel.writeString(serverName)
        parcel.writeString(version)
        parcel.writeString(description)
        parcel.writeStringList(capabilities)
        parcel.writeString(protocolVersion)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<McpServerInfo> {
        override fun createFromParcel(parcel: Parcel): McpServerInfo {
            return McpServerInfo(parcel)
        }

        override fun newArray(size: Int): Array<McpServerInfo?> {
            return arrayOfNulls(size)
        }
    }
}