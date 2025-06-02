package io.rosenpin.mmcp.server.base

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import io.rosenpin.mmcp.server.MCPServiceBase

/**
 * Base class for MCP servers that need access to Android Context and system services.
 * 
 * This class provides convenient access to Android context, system services, and
 * permission checking functionality that most MCP servers will need.
 * 
 * Usage:
 * ```kotlin
 * @MCPServer(id = "com.example.myserver", name = "My Server")
 * class MyMCPService : ContextAwareMCPService() {
 *     
 *     @MCPTool(id = "get_device_info", name = "Get Device Info")
 *     fun getDeviceInfo(): String {
 *         val telephonyManager = getSystemService<TelephonyManager>()
 *         return "Device: ${Build.MODEL}, Carrier: ${telephonyManager.networkOperatorName}"
 *     }
 * }
 * ```
 */
abstract class ContextAwareMCPService : MCPServiceBase() {
    
    companion object {
        private const val TAG = "ContextAwareMCPService"
    }
    
    /**
     * Get the application context.
     * This is safer than using the service context for long-lived operations.
     */
    protected val appContext: Context get() = applicationContext
    
    /**
     * Get a system service by type.
     * 
     * @param T The type of system service to retrieve
     * @return The system service instance, or null if not available
     */
    protected fun <T : Any> getTypedSystemService(serviceClass: Class<T>): T? {
        return try {
            appContext.getSystemService(serviceClass)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get system service ${serviceClass.simpleName}", e)
            null
        }
    }
    
    /**
     * Get a system service by name.
     * 
     * @param serviceName The name of the system service (e.g., Context.TELEPHONY_SERVICE)
     * @return The system service instance, or null if not available
     */
    @Suppress("DEPRECATION")
    protected fun getSystemServiceByName(serviceName: String): Any? {
        return try {
            appContext.getSystemService(serviceName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get system service $serviceName", e)
            null
        }
    }
    
    /**
     * Check if the server itself has a specific permission.
     * 
     * This is the correct MCP pattern - the server needs permissions to access protected resources
     * and act as a secure gateway for clients.
     * 
     * @param permission The permission to check (e.g., Manifest.permission.READ_CONTACTS)
     * @return true if the server has the permission, false otherwise
     */
    protected fun checkServerPermission(permission: String): Boolean {
        return try {
            // Check if this server app has the permission
            val result = appContext.checkSelfPermission(permission)
            result == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check server permission $permission", e)
            false
        }
    }
    
    /**
     * Check if the calling package has a specific permission.
     * 
     * NOTE: In MCP architecture, you usually want checkServerPermission instead.
     * This method is retained for cases where you need to verify client identity.
     * 
     * @param permission The permission to check (e.g., custom app permissions)
     * @return true if the caller has the permission, false otherwise
     */
    protected fun checkCallerPermission(permission: String): Boolean {
        return try {
            val callingUid = Binder.getCallingUid()
            val callingPid = Binder.getCallingPid()
            
            // Check permission for the calling process
            val result = checkPermission(permission, callingPid, callingUid)
            result == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check caller permission $permission", e)
            false
        }
    }
    
    /**
     * Get the package name of the calling application.
     * 
     * @return The package name of the caller, or null if it cannot be determined
     */
    protected fun getCallerPackageName(): String? {
        return try {
            val callingUid = Binder.getCallingUid()
            packageManager.getNameForUid(callingUid)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get caller package name", e)
            null
        }
    }
    
    /**
     * Check if the calling package is a specific package.
     * 
     * This can be used to restrict MCP tools to specific trusted applications.
     * 
     * @param packageName The package name to check against
     * @return true if the caller is the specified package
     */
    protected fun isCallerPackage(packageName: String): Boolean {
        return getCallerPackageName() == packageName
    }
    
    /**
     * Require that the server itself has a specific permission, throwing an exception if not.
     * 
     * This is the correct MCP pattern - the server needs permissions, not the client.
     * The server acts as a secure gateway to protected resources.
     * 
     * @param permission The required permission
     * @throws SecurityException if the server doesn't have the permission
     */
    protected fun requireServerPermission(permission: String) {
        if (!checkServerPermission(permission)) {
            throw SecurityException("""
                MCP Server ${appContext.packageName} does not have permission: $permission
                
                The MCP architecture requires the SERVER to have permissions, not the client.
                Please ensure:
                1. The permission is declared in the server's AndroidManifest.xml
                2. The user has granted this permission to the server app
                
                The client app (${getCallerPackageName()}) does NOT need this permission.
            """.trimIndent())
        }
    }
    
    /**
     * Require that the caller is a specific package, throwing an exception if not.
     * 
     * @param allowedPackage The required package name
     * @throws SecurityException if the caller is not the specified package
     */
    protected fun requireCallerPackage(allowedPackage: String) {
        val callerPackage = getCallerPackageName()
        if (callerPackage != allowedPackage) {
            throw SecurityException("Caller $callerPackage is not authorized (expected: $allowedPackage)")
        }
    }
    
    /**
     * Log information about the current caller.
     * Useful for debugging and security auditing.
     */
    protected fun logCallerInfo() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val callingUid = Binder.getCallingUid()
            val callingPid = Binder.getCallingPid()
            val callerPackage = getCallerPackageName()
            
            Log.d(TAG, "MCP call from package: $callerPackage (UID: $callingUid, PID: $callingPid)")
        }
    }
}