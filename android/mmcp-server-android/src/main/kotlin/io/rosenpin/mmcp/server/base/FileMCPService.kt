package io.rosenpin.mmcp.server.base

import android.net.Uri
import android.util.Log
import io.rosenpin.mmcp.server.annotations.MCPResource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Base class for MCP servers that provide file system resources.
 * 
 * This class provides secure file access with built-in path validation and
 * permission checking. It's designed to safely expose file resources via MCP
 * while preventing directory traversal and unauthorized access.
 * 
 * Usage:
 * ```kotlin
 * @MCPServer(id = "com.example.fileserver", name = "File Server")
 * class MyFileServer : FileMCPService() {
 *     
 *     override fun getAllowedDirectories(): List<File> {
 *         return listOf(
 *             File(appContext.filesDir, "documents"),
 *             File(appContext.cacheDir, "temp")
 *         )
 *     }
 *     
 *     @MCPTool(id = "list_files", name = "List Files")
 *     fun listFiles(@MCPParam("directory") directory: String): String {
 *         val dir = File(directory)
 *         if (!isFileAccessAllowed(dir)) {
 *             return "Access denied to directory: $directory"
 *         }
 *         return dir.listFiles()?.joinToString("\n") { it.name } ?: "Directory not found"
 *     }
 * }
 * ```
 */
abstract class FileMCPService : ContextAwareMCPService() {
    
    companion object {
        protected const val TAG = "FileMCPService"
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB default limit
    }
    
    /**
     * Override this method to specify which directories this server is allowed to access.
     * By default, only allows access to the app's private directories.
     * 
     * @return List of directories that this server can access
     */
    protected open fun getAllowedDirectories(): List<File> {
        return listOf(
            appContext.filesDir,
            appContext.cacheDir,
            appContext.getExternalFilesDir(null) ?: appContext.filesDir
        ).filterNotNull()
    }
    
    /**
     * Override this method to specify the maximum file size that can be read.
     * Default is 10MB.
     * 
     * @return Maximum file size in bytes
     */
    protected open fun getMaxFileSize(): Long = MAX_FILE_SIZE.toLong()
    
    /**
     * Check if access to a specific file or directory is allowed.
     * 
     * @param file The file to check
     * @return true if access is allowed, false otherwise
     */
    protected fun isFileAccessAllowed(file: File): Boolean {
        try {
            val canonicalPath = file.canonicalPath
            val allowedDirs = getAllowedDirectories()
            
            return allowedDirs.any { allowedDir ->
                try {
                    canonicalPath.startsWith(allowedDir.canonicalPath)
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to get canonical path for allowed directory: ${allowedDir.path}", e)
                    false
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to get canonical path for file: ${file.path}", e)
            return false
        }
    }
    
    /**
     * Safely read a file as text, with security and size checks.
     * 
     * @param file The file to read
     * @param encoding The text encoding to use (default: UTF-8)
     * @return The file contents as a string
     * @throws SecurityException if access to the file is not allowed
     * @throws FileNotFoundException if the file doesn't exist
     * @throws IOException if the file is too large or cannot be read
     */
    protected fun readFileAsText(file: File, encoding: String = "UTF-8"): String {
        if (!isFileAccessAllowed(file)) {
            throw SecurityException("Access denied to file: ${file.path}")
        }
        
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.path}")
        }
        
        if (!file.isFile) {
            throw IOException("Path is not a file: ${file.path}")
        }
        
        if (file.length() > getMaxFileSize()) {
            throw IOException("File too large: ${file.length()} bytes (max: ${getMaxFileSize()})")
        }
        
        return try {
            file.readText(charset(encoding))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: ${file.path}", e)
            throw IOException("Failed to read file: ${e.message}")
        }
    }
    
    /**
     * Safely read a file as bytes, with security and size checks.
     * 
     * @param file The file to read
     * @return The file contents as a byte array
     * @throws SecurityException if access to the file is not allowed
     * @throws FileNotFoundException if the file doesn't exist
     * @throws IOException if the file is too large or cannot be read
     */
    protected fun readFileAsBytes(file: File): ByteArray {
        if (!isFileAccessAllowed(file)) {
            throw SecurityException("Access denied to file: ${file.path}")
        }
        
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.path}")
        }
        
        if (!file.isFile) {
            throw IOException("Path is not a file: ${file.path}")
        }
        
        if (file.length() > getMaxFileSize()) {
            throw IOException("File too large: ${file.length()} bytes (max: ${getMaxFileSize()})")
        }
        
        return try {
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: ${file.path}", e)
            throw IOException("Failed to read file: ${e.message}")
        }
    }
    
    /**
     * List files in a directory with security checks.
     * 
     * @param directory The directory to list
     * @param includeHidden Whether to include hidden files (default: false)
     * @return List of files in the directory
     * @throws SecurityException if access to the directory is not allowed
     * @throws FileNotFoundException if the directory doesn't exist
     */
    protected fun listFiles(directory: File, includeHidden: Boolean = false): List<File> {
        if (!isFileAccessAllowed(directory)) {
            throw SecurityException("Access denied to directory: ${directory.path}")
        }
        
        if (!directory.exists()) {
            throw FileNotFoundException("Directory not found: ${directory.path}")
        }
        
        if (!directory.isDirectory) {
            throw IOException("Path is not a directory: ${directory.path}")
        }
        
        val files = directory.listFiles()
            ?: throw IOException("Failed to list directory contents: ${directory.path}")
        
        return if (includeHidden) {
            files.toList()
        } else {
            files.filter { !it.isHidden }
        }
    }
    
    /**
     * Default file resource handler that provides file:// URI access.
     * 
     * This method is automatically registered as an MCP resource handler.
     * Override getAllowedDirectories() to control which files can be accessed.
     * 
     * Supported URI formats:
     * - file:///absolute/path/to/file
     * - file://relative/path/to/file (relative to allowed directories)
     */
    @MCPResource(
        scheme = "file",
        name = "File Resource",
        description = "Access files from the filesystem",
        mimeType = "application/octet-stream"
    )
    protected open fun getFileResource(uri: String): ByteArray {
        Log.d(TAG, "Accessing file resource: $uri")
        
        val parsedUri = Uri.parse(uri)
        val path = parsedUri.path ?: throw IllegalArgumentException("Invalid file URI: $uri")
        
        val file = if (path.startsWith("/")) {
            // Absolute path
            File(path)
        } else {
            // Relative path - try each allowed directory
            val allowedDirs = getAllowedDirectories()
            val possibleFiles = allowedDirs.map { File(it, path) }
            possibleFiles.firstOrNull { it.exists() }
                ?: throw FileNotFoundException("File not found in any allowed directory: $path")
        }
        
        return readFileAsBytes(file)
    }
    
    /**
     * Convenience method to create file URIs for use with the file resource handler.
     * 
     * @param file The file to create a URI for
     * @return A file:// URI string
     */
    protected fun createFileUri(file: File): String {
        return "file://${file.path}"
    }
    
    /**
     * Get file information as a formatted string.
     * 
     * @param file The file to get information about
     * @return Formatted string with file information
     */
    protected fun getFileInfo(file: File): String {
        if (!isFileAccessAllowed(file)) {
            return "Access denied"
        }
        
        if (!file.exists()) {
            return "File not found"
        }
        
        return buildString {
            appendLine("Path: ${file.path}")
            appendLine("Name: ${file.name}")
            appendLine("Size: ${file.length()} bytes")
            appendLine("Last modified: ${java.util.Date(file.lastModified())}")
            appendLine("Type: ${if (file.isDirectory) "Directory" else "File"}")
            appendLine("Readable: ${file.canRead()}")
            appendLine("Writable: ${file.canWrite()}")
            appendLine("Hidden: ${file.isHidden}")
        }
    }
}