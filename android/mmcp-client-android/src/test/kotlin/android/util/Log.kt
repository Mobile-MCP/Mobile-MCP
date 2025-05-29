@file:JvmName("Log")
package android.util

/**
 * Mock Android Log for unit tests
 */
object Log {
    @JvmStatic
    fun d(tag: String?, msg: String): Int {
        println("DEBUG/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun i(tag: String?, msg: String): Int {
        println("INFO/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String?, msg: String): Int {
        println("WARN/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String?, msg: String, tr: Throwable?): Int {
        println("WARN/$tag: $msg")
        tr?.printStackTrace()
        return 0
    }

    @JvmStatic
    fun e(tag: String?, msg: String): Int {
        println("ERROR/$tag: $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String?, msg: String, tr: Throwable?): Int {
        println("ERROR/$tag: $msg")
        tr?.printStackTrace()
        return 0
    }
}