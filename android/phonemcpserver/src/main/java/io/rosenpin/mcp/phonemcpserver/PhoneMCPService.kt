package io.rosenpin.mcp.phonemcpserver

import android.Manifest
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import io.rosenpin.mmcp.server.annotations.*
import io.rosenpin.mmcp.server.base.ContextAwareMCPService
import java.text.SimpleDateFormat
import java.util.*

@MCPServer(
    id = "io.rosenpin.mcp.phonemcpserver",
    name = "Phone & Contacts MCP Server",
    description = "Provides access to device contacts and phone calling functionality",
    version = "1.0.0"
)
class PhoneMCPService : ContextAwareMCPService() {
    
    companion object {
        private const val TAG = "PhoneMCPService"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    @MCPTool(
        id = "get_contacts",
        name = "Get Contacts",
        description = "Retrieve all contacts from the device",
        parameters = """
        {
            "type": "object",
            "properties": {
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of contacts to return",
                    "default": 100
                }
            }
        }
        """
    )
    fun getContacts(@MCPParam("limit") limit: Int = 100): String {
        requireServerPermission(Manifest.permission.READ_CONTACTS)
        
        val contacts = mutableListOf<Map<String, Any>>()
        val cursor: Cursor? = appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC LIMIT $limit"
        )
        
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            
            while (it.moveToNext()) {
                val contact = mapOf(
                    "id" to it.getString(idIndex),
                    "name" to it.getString(nameIndex),
                    "phoneNumber" to it.getString(numberIndex)
                )
                contacts.add(contact)
            }
        }
        
        return "Found ${contacts.size} contacts: ${contacts.take(10).joinToString(", ") { "${it["name"]}: ${it["phoneNumber"]}" }}" +
                if (contacts.size > 10) "... and ${contacts.size - 10} more" else ""
    }

    @MCPTool(
        id = "get_contact_by_name",
        name = "Get Contact by Name",
        description = "Find a contact by name and return their phone number",
        parameters = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "description": "Name of the contact to search for"
                }
            },
            "required": ["name"]
        }
        """
    )
    fun getContactByName(@MCPParam("name") name: String): String {
        requireServerPermission(Manifest.permission.READ_CONTACTS)
        
        val cursor: Cursor? = appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        
        val results = mutableListOf<String>()
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (it.moveToNext()) {
                val contactName = it.getString(nameIndex)
                val phoneNumber = it.getString(numberIndex)
                results.add("$contactName: $phoneNumber")
            }
        }
        
        return if (results.isNotEmpty()) {
            "Found ${results.size} matches: ${results.joinToString(", ")}"
        } else {
            "No contacts found matching '$name'"
        }
    }

    @MCPTool(
        id = "make_call",
        name = "Make Phone Call",
        description = "Initiate a phone call to the specified number",
        parameters = """
        {
            "type": "object",
            "properties": {
                "phoneNumber": {
                    "type": "string",
                    "description": "Phone number to call"
                }
            },
            "required": ["phoneNumber"]
        }
        """
    )
    fun makeCall(@MCPParam("phoneNumber") phoneNumber: String): String {
        requireServerPermission(Manifest.permission.CALL_PHONE)
        
        try {
            val telecomManager = getTypedSystemService(TelecomManager::class.java)
            val uri = Uri.fromParts("tel", phoneNumber, null)
            
            telecomManager?.placeCall(uri, null)
            return "Call initiated to $phoneNumber"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            return "Failed to make call: ${e.message}"
        }
    }

    @MCPTool(
        id = "get_call_history",
        name = "Get Call History",
        description = "Retrieve recent call history",
        parameters = """
        {
            "type": "object",
            "properties": {
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of call records to return",
                    "default": 20
                },
                "callType": {
                    "type": "string",
                    "description": "Type of calls to filter",
                    "enum": ["all", "incoming", "outgoing", "missed"],
                    "default": "all"
                }
            }
        }
        """
    )
    fun getCallHistory(@MCPParam("limit") limit: Int = 20, @MCPParam("callType") callType: String = "all"): String {
        requireServerPermission(Manifest.permission.READ_CALL_LOG)
        
        val selection = when (callType) {
            "incoming" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE}"
            "outgoing" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}"
            "missed" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"
            else -> null
        }
        
        val cursor: Cursor? = appContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            selection,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )
        
        val calls = mutableListOf<String>()
        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            
            while (it.moveToNext()) {
                val number = it.getString(numberIndex) ?: "Unknown"
                val date = Date(it.getLong(dateIndex))
                val duration = it.getInt(durationIndex)
                val type = when (it.getInt(typeIndex)) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Unknown"
                }
                
                calls.add("$type call from $number on ${dateFormat.format(date)} (${duration}s)")
            }
        }
        
        return if (calls.isNotEmpty()) {
            "Found ${calls.size} calls: ${calls.joinToString("; ")}"
        } else {
            "No call history found"
        }
    }

    @MCPResource(
        scheme = "contact",
        name = "Contact Resource",
        description = "Access individual contact information via contact://contact_id URI",
        mimeType = "application/json"
    )
    fun getContactResource(uri: String): String {
        requireServerPermission(Manifest.permission.READ_CONTACTS)
        
        val contactId = uri.substringAfter("contact://")
        val cursor: Cursor? = appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )
        
        val contactInfo = mutableMapOf<String, Any>()
        val phoneNumbers = mutableListOf<Map<String, String>>()
        
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            
            while (it.moveToNext()) {
                if (contactInfo["name"] == null) {
                    contactInfo["name"] = it.getString(nameIndex)
                    contactInfo["id"] = contactId
                }
                
                phoneNumbers.add(mapOf(
                    "number" to it.getString(numberIndex),
                    "type" to getPhoneTypeLabel(it.getInt(typeIndex))
                ))
            }
        }
        
        contactInfo["phoneNumbers"] = phoneNumbers
        return "Contact: ${contactInfo["name"]}, Numbers: ${phoneNumbers.joinToString(", ") { "${it["type"]}: ${it["number"]}" }}"
    }

    @MCPPrompt(
        id = "contact_summary",
        name = "Contact Summary",
        description = "Generate a summary for a contact including their communication history",
        parameters = """
        {
            "type": "object",
            "properties": {
                "contactName": {
                    "type": "string",
                    "description": "Name of the contact to summarize"
                }
            },
            "required": ["contactName"]
        }
        """
    )
    fun generateContactSummary(@MCPParam("contactName") contactName: String): String {
        requireServerPermission(Manifest.permission.READ_CONTACTS)
        requireServerPermission(Manifest.permission.READ_CALL_LOG)
        
        return "Contact Summary for $contactName: This contact has been in your phone for analysis. Recent communication patterns and interaction history would be displayed here."
    }

    private fun getPhoneTypeLabel(type: Int): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Home Fax"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Work Fax"
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
            else -> "Other"
        }
    }
}