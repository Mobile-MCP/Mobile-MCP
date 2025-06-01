package io.rosenpin.mmcp.server.base

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.ContactsContract
import android.provider.CalendarContract
import android.util.Log
import io.rosenpin.mmcp.server.annotations.MCPResource
import java.io.IOException

/**
 * Base class for MCP servers that provide access to Android ContentProvider data.
 * 
 * This class provides secure access to content providers with built-in permission
 * checking and common query helpers for media, contacts, calendar, and other
 * Android content providers.
 * 
 * Usage:
 * ```kotlin
 * @MCPServer(id = "com.example.contentserver", name = "Content Server")
 * class MyContentServer : ContentProviderMCPService() {
 *     
 *     @MCPTool(id = "get_contacts_count", name = "Get Contacts Count")
 *     fun getContactsCount(): String {
 *         requireCallerPermission(android.Manifest.permission.READ_CONTACTS)
 *         
 *         val count = queryContentProvider(
 *             ContactsContract.Contacts.CONTENT_URI,
 *             arrayOf("COUNT(*) as count")
 *         ).use { cursor ->
 *             if (cursor?.moveToFirst() == true) {
 *                 cursor.getInt(0)
 *             } else {
 *                 0
 *             }
 *         }
 *         
 *         return "Total contacts: $count"
 *     }
 * }
 * ```
 */
abstract class ContentProviderMCPService : ContextAwareMCPService() {
    
    companion object {
        protected const val TAG = "ContentProviderMCPService"
        private const val MAX_CONTENT_SIZE = 5 * 1024 * 1024 // 5MB default limit
    }
    
    /**
     * Override this method to specify the maximum content size that can be read.
     * Default is 5MB.
     * 
     * @return Maximum content size in bytes
     */
    protected open fun getMaxContentSize(): Long = MAX_CONTENT_SIZE.toLong()
    
    /**
     * Safely query a content provider with automatic cursor management.
     * 
     * @param uri The content URI to query
     * @param projection The columns to return (null for all columns)
     * @param selection The WHERE clause (null for all rows)
     * @param selectionArgs Arguments for the WHERE clause
     * @param sortOrder How to sort the results (null for default)
     * @return A cursor with the query results (must be closed by caller)
     * @throws SecurityException if the caller doesn't have the required permissions
     */
    protected fun queryContentProvider(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Cursor? {
        return try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception querying content provider: $uri", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error querying content provider: $uri", e)
            null
        }
    }
    
    /**
     * Read content from a content URI as bytes.
     * 
     * @param uri The content URI to read from
     * @return The content as a byte array
     * @throws SecurityException if access is denied
     * @throws IOException if the content cannot be read or is too large
     */
    protected fun readContentAsBytes(uri: Uri): ByteArray {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val contentLength = try {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        afd.length
                    } ?: -1L
                } catch (e: Exception) {
                    -1L // Unknown size
                }
                
                if (contentLength > getMaxContentSize()) {
                    throw IOException("Content too large: $contentLength bytes (max: ${getMaxContentSize()})")
                }
                
                inputStream.readBytes()
            } ?: throw IOException("Failed to open input stream for URI: $uri")
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception reading content: $uri", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reading content: $uri", e)
            throw IOException("Failed to read content: ${e.message}")
        }
    }
    
    /**
     * Read content from a content URI as text.
     * 
     * @param uri The content URI to read from
     * @param encoding The text encoding to use (default: UTF-8)
     * @return The content as a string
     * @throws SecurityException if access is denied
     * @throws IOException if the content cannot be read or is too large
     */
    protected fun readContentAsText(uri: Uri, encoding: String = "UTF-8"): String {
        val bytes = readContentAsBytes(uri)
        return String(bytes, charset(encoding))
    }
    
    /**
     * Get information about a content URI.
     * 
     * @param uri The content URI to get information about
     * @return Formatted string with content information
     */
    protected fun getContentInfo(uri: Uri): String {
        return try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            
            queryContentProvider(uri, projection)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    buildString {
                        appendLine("URI: $uri")
                        
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            appendLine("Name: ${cursor.getString(nameIndex)}")
                        }
                        
                        val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        if (sizeIndex >= 0) {
                            appendLine("Size: ${cursor.getLong(sizeIndex)} bytes")
                        }
                        
                        val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                        if (mimeIndex >= 0) {
                            appendLine("MIME Type: ${cursor.getString(mimeIndex)}")
                        }
                        
                        val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (dateIndex >= 0) {
                            val date = java.util.Date(cursor.getLong(dateIndex) * 1000)
                            appendLine("Modified: $date")
                        }
                    }
                } else {
                    "No information available for URI: $uri"
                }
            } ?: "Failed to query content URI: $uri"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get content info for URI: $uri", e)
            "Error getting content info: ${e.message}"
        }
    }
    
    /**
     * Default content resource handler that provides content:// URI access.
     * 
     * This method is automatically registered as an MCP resource handler.
     * Callers must have appropriate permissions for the content they're accessing.
     * 
     * Supported URI formats:
     * - content://media/external/images/media/123
     * - content://com.android.contacts/contacts/123
     * - Any valid content:// URI
     */
    @MCPResource(
        scheme = "content",
        name = "Content Provider Resource",
        description = "Access content from Android content providers",
        mimeType = "application/octet-stream"
    )
    protected open fun getContentResource(uri: String): ByteArray {
        Log.d(TAG, "Accessing content resource: $uri")
        
        val contentUri = Uri.parse(uri)
        if (contentUri.scheme != "content") {
            throw IllegalArgumentException("URI must use content:// scheme: $uri")
        }
        
        return readContentAsBytes(contentUri)
    }
    
    /**
     * Helper method to check if a specific permission is required for a content URI.
     * 
     * @param uri The content URI to check
     * @param permission The permission to check
     * @return true if the permission is required and the caller has it
     */
    protected fun checkContentPermission(uri: Uri, permission: String): Boolean {
        return try {
            checkCallerPermission(permission)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking content permission for URI: $uri", e)
            false
        }
    }
    
    /**
     * Get the MIME type for a content URI.
     * 
     * @param uri The content URI
     * @return The MIME type, or null if it cannot be determined
     */
    protected fun getContentType(uri: Uri): String? {
        return try {
            contentResolver.getType(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get content type for URI: $uri", e)
            null
        }
    }
    
    /**
     * Helper method to query contacts with proper permission checking.
     * 
     * @param projection The columns to return
     * @param selection The WHERE clause
     * @param selectionArgs Arguments for the WHERE clause
     * @param sortOrder How to sort the results
     * @return A cursor with contacts data
     * @throws SecurityException if READ_CONTACTS permission is not granted
     */
    protected fun queryContacts(
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Cursor? {
        requireCallerPermission(android.Manifest.permission.READ_CONTACTS)
        return queryContentProvider(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
    }
    
    /**
     * Helper method to query calendar events with proper permission checking.
     * 
     * @param projection The columns to return
     * @param selection The WHERE clause
     * @param selectionArgs Arguments for the WHERE clause
     * @param sortOrder How to sort the results
     * @return A cursor with calendar events data
     * @throws SecurityException if READ_CALENDAR permission is not granted
     */
    protected fun queryCalendarEvents(
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Cursor? {
        requireCallerPermission(android.Manifest.permission.READ_CALENDAR)
        return queryContentProvider(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
    }
    
    /**
     * Helper method to query media files with proper permission checking.
     * 
     * @param mediaType The type of media (images, audio, video)
     * @param projection The columns to return
     * @param selection The WHERE clause
     * @param selectionArgs Arguments for the WHERE clause
     * @param sortOrder How to sort the results
     * @return A cursor with media files data
     * @throws SecurityException if appropriate media permissions are not granted
     */
    protected fun queryMediaFiles(
        mediaType: MediaType,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Cursor? {
        // Check appropriate permissions based on media type
        when (mediaType) {
            MediaType.IMAGES -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requireCallerPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    requireCallerPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            MediaType.AUDIO -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requireCallerPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    requireCallerPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            MediaType.VIDEO -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requireCallerPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    requireCallerPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
        
        val uri = when (mediaType) {
            MediaType.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        return queryContentProvider(uri, projection, selection, selectionArgs, sortOrder)
    }
    
    /**
     * Enumeration of media types for use with queryMediaFiles.
     */
    enum class MediaType {
        IMAGES,
        AUDIO,
        VIDEO
    }
}