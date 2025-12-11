package io.github.nitsuya.aa.display.xposed.util

import android.content.Context
import android.os.Build
import io.github.nitsuya.aa.display.xposed.log

/**
 * Enhanced logging utility for probe mode debugging.
 * Captures detailed information about reflection attempts, class loading, and method invocations.
 */
object ProbeLogger {
    private const val TAG = "AAD_ProbeLogger"

    private var isProbeMode = false
    private val probeHistory = mutableListOf<ProbeEntry>()
    private const val MAX_HISTORY_SIZE = 100

    data class ProbeEntry(
        val timestamp: Long,
        val type: ProbeType,
        val className: String?,
        val methodName: String?,
        val dexPath: String?,
        val success: Boolean,
        val errorMessage: String?,
        val stackTrace: String?
    )

    enum class ProbeType {
        CLASS_LOAD,
        METHOD_LOOKUP,
        METHOD_INVOKE,
        DEX_LOAD,
        HOOK_INSTALL
    }

    /**
     * Enable or disable probe mode.
     */
    fun setProbeMode(enabled: Boolean) {
        isProbeMode = enabled
        log(TAG, "Probe mode ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if probe mode is enabled.
     */
    fun isProbeMode(): Boolean = isProbeMode

    /**
     * Log a class loading attempt.
     */
    fun logClassLoad(className: String, dexPath: String? = null, success: Boolean, error: Throwable? = null) {
        if (!isProbeMode && success) return // Only log failures in non-probe mode

        val entry = ProbeEntry(
            timestamp = System.currentTimeMillis(),
            type = ProbeType.CLASS_LOAD,
            className = className,
            methodName = null,
            dexPath = dexPath,
            success = success,
            errorMessage = error?.message,
            stackTrace = error?.stackTraceToString()
        )

        addEntry(entry)

        if (!success || isProbeMode) {
            val sb = StringBuilder()
            sb.append("=== Class Load ${if (success) "SUCCESS" else "FAILED"} ===\n")
            sb.append("Class: $className\n")
            if (dexPath != null) sb.append("DEX Path: $dexPath\n")
            sb.append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            if (error != null) {
                sb.append("Error: ${error.javaClass.simpleName} - ${error.message}\n")
            }
            sb.append("=================================")
            log(TAG, sb.toString())
        }
    }

    /**
     * Log a method lookup attempt.
     */
    fun logMethodLookup(className: String, methodName: String, paramTypes: Array<out Class<*>>?, success: Boolean, error: Throwable? = null) {
        if (!isProbeMode && success) return

        val entry = ProbeEntry(
            timestamp = System.currentTimeMillis(),
            type = ProbeType.METHOD_LOOKUP,
            className = className,
            methodName = "$methodName(${paramTypes?.joinToString { it.simpleName } ?: ""})",
            dexPath = null,
            success = success,
            errorMessage = error?.message,
            stackTrace = error?.stackTraceToString()
        )

        addEntry(entry)

        if (!success || isProbeMode) {
            val sb = StringBuilder()
            sb.append("=== Method Lookup ${if (success) "SUCCESS" else "FAILED"} ===\n")
            sb.append("Class: $className\n")
            sb.append("Method: $methodName\n")
            if (paramTypes != null) {
                sb.append("Params: ${paramTypes.joinToString { it.simpleName }}\n")
            }
            sb.append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            if (error != null) {
                sb.append("Error: ${error.javaClass.simpleName} - ${error.message}\n")
            }
            sb.append("=================================")
            log(TAG, sb.toString())
        }
    }

    /**
     * Log a method invocation attempt.
     */
    fun logMethodInvoke(methodName: String, success: Boolean, error: Throwable? = null) {
        if (!isProbeMode && success) return

        val entry = ProbeEntry(
            timestamp = System.currentTimeMillis(),
            type = ProbeType.METHOD_INVOKE,
            className = null,
            methodName = methodName,
            dexPath = null,
            success = success,
            errorMessage = error?.message,
            stackTrace = error?.stackTraceToString()
        )

        addEntry(entry)

        if (!success || isProbeMode) {
            val sb = StringBuilder()
            sb.append("=== Method Invoke ${if (success) "SUCCESS" else "FAILED"} ===\n")
            sb.append("Method: $methodName\n")
            if (error != null) {
                sb.append("Error: ${error.javaClass.simpleName} - ${error.message}\n")
                if (isProbeMode) {
                    sb.append("Stack trace:\n")
                    error.stackTrace.take(10).forEach {
                        sb.append("  at $it\n")
                    }
                }
            }
            sb.append("=================================")
            log(TAG, sb.toString())
        }
    }

    /**
     * Log a DEX loading attempt.
     */
    fun logDexLoad(dexPath: String, success: Boolean, error: Throwable? = null) {
        if (!isProbeMode && success) return

        val entry = ProbeEntry(
            timestamp = System.currentTimeMillis(),
            type = ProbeType.DEX_LOAD,
            className = null,
            methodName = null,
            dexPath = dexPath,
            success = success,
            errorMessage = error?.message,
            stackTrace = error?.stackTraceToString()
        )

        addEntry(entry)

        if (!success || isProbeMode) {
            val sb = StringBuilder()
            sb.append("=== DEX Load ${if (success) "SUCCESS" else "FAILED"} ===\n")
            sb.append("DEX Path: $dexPath\n")
            sb.append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            if (error != null) {
                sb.append("Error: ${error.javaClass.simpleName} - ${error.message}\n")
            }
            sb.append("=================================")
            log(TAG, sb.toString())
        }
    }

    /**
     * Log a hook installation attempt.
     */
    fun logHookInstall(hookName: String, className: String, methodName: String?, success: Boolean, error: Throwable? = null) {
        if (!isProbeMode && success) return

        val entry = ProbeEntry(
            timestamp = System.currentTimeMillis(),
            type = ProbeType.HOOK_INSTALL,
            className = className,
            methodName = methodName,
            dexPath = null,
            success = success,
            errorMessage = error?.message,
            stackTrace = error?.stackTraceToString()
        )

        addEntry(entry)

        if (!success || isProbeMode) {
            val sb = StringBuilder()
            sb.append("=== Hook Install ${if (success) "SUCCESS" else "FAILED"} ===\n")
            sb.append("Hook: $hookName\n")
            sb.append("Class: $className\n")
            if (methodName != null) sb.append("Method: $methodName\n")
            if (error != null) {
                sb.append("Error: ${error.javaClass.simpleName} - ${error.message}\n")
            }
            sb.append("=================================")
            log(TAG, sb.toString())
        }
    }

    /**
     * Log detailed environment information.
     */
    fun logEnvironmentInfo(context: Context? = null) {
        val sb = StringBuilder()
        sb.append("=== AADisplay Environment Info ===\n")
        sb.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Build: ${Build.FINGERPRINT}\n")
        sb.append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}\n")

        if (context != null) {
            try {
                val aaVersion = VersionUtils.getAppVersion(context, VersionUtils.PACKAGE_ANDROID_AUTO)
                sb.append("Android Auto Version: ${aaVersion ?: "Not installed"}\n")
                sb.append("AAD Enabled: ${VersionUtils.isAadEnabled(context)}\n")
            } catch (e: Throwable) {
                sb.append("Failed to get app info: ${e.message}\n")
            }
        }

        sb.append("Probe Mode: $isProbeMode\n")
        sb.append("=================================")
        log(TAG, sb.toString())
    }

    /**
     * Get probe history summary.
     */
    fun getHistorySummary(): String {
        val sb = StringBuilder()
        sb.append("=== Probe History Summary ===\n")
        sb.append("Total entries: ${probeHistory.size}\n")

        ProbeType.values().forEach { type ->
            val entries = probeHistory.filter { it.type == type }
            val successes = entries.count { it.success }
            val failures = entries.count { !it.success }
            sb.append("$type: $successes success, $failures failed\n")
        }

        sb.append("\nRecent failures:\n")
        probeHistory.filter { !it.success }.takeLast(10).forEach { entry ->
            sb.append("  ${entry.type}: ${entry.className ?: ""}${entry.methodName?.let { "#$it" } ?: ""}\n")
            sb.append("    Error: ${entry.errorMessage}\n")
        }

        sb.append("=================================")
        return sb.toString()
    }

    /**
     * Clear probe history.
     */
    fun clearHistory() {
        probeHistory.clear()
        log(TAG, "Probe history cleared")
    }

    private fun addEntry(entry: ProbeEntry) {
        synchronized(probeHistory) {
            probeHistory.add(entry)
            if (probeHistory.size > MAX_HISTORY_SIZE) {
                probeHistory.removeAt(0)
            }
        }
    }
}
