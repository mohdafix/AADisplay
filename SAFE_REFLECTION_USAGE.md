# Safe Reflection & DEX Loading Usage Guide

This document explains how to use the new safe reflection utilities in AADisplay to prevent crashes from missing classes/methods and improve debugging.

## Overview

The following utilities have been added to improve reflection safety:

1. **ReflectUtils.java** - Core safe reflection methods with Optional-based API
2. **VersionUtils.kt** - Version detection and feature flag management
3. **ProbeLogger.kt** - Enhanced logging for debugging reflection issues

## Quick Start

### 1. Basic Class Loading

**Old way (unsafe):**
```java
Class<?> cls = Class.forName("com.example.SomeClass");
Method m = cls.getMethod("someMethod", String.class);
m.invoke(instance, "arg");
```

**New way (safe):**
```java
ReflectUtils.safeForName("com.example.SomeClass", null).ifPresent(cls -> {
    ReflectUtils.safeGetMethod(cls, "someMethod", String.class).ifPresent(method -> {
        ReflectUtils.safeInvoke(method, instance, "arg");
    });
});
```

### 2. DEX Loading

**Old way:**
```java
DexClassLoader loader = new DexClassLoader(dexPath, optDir, null, parentLoader);
Class<?> cls = Class.forName(className, false, loader);
```

**New way:**
```java
ClassLoader loader = ReflectUtils.safeLoadDex(dexPath, context);
if (loader != null) {
    ReflectUtils.safeForName(className, loader).ifPresent(cls -> {
        // Use the class
    });
}
```

**Or use the combined method:**
```java
Class<?> cls = ReflectUtils.loadDexClassSafe(dexPath, className, context);
if (cls != null) {
    // Use the class
}
```

### 3. Feature Detection with Fallback

```kotlin
// Check if feature is available before using it
val maybeClass = ReflectUtils.safeForName("com.example.FeatureClass", null)
if (!maybeClass.isPresent) {
    log(TAG, "Feature class missing, disabling feature")
    return
}

val cls = maybeClass.get()
val maybeMethod = ReflectUtils.safeGetMethod(cls, "featureMethod", String::class.java)
if (!maybeMethod.isPresent) {
    log(TAG, "Feature method missing, using fallback")
    // Use fallback implementation
    return
}

// Feature is available, use it
val method = maybeMethod.get()
ReflectUtils.safeInvoke(method, null, "arg")
```

### 4. Version-Based Feature Gating

```kotlin
// Check Android Auto version before enabling features
val aaVersion = VersionUtils.getAppVersion(context, VersionUtils.PACKAGE_ANDROID_AUTO)
if (aaVersion != null && aaVersion.startsWith("8.")) {
    // Enable features for version 8.x
    enableNewFeature()
} else {
    // Use fallback for older versions
    enableLegacyFeature()
}

// Or use the built-in comparison
if (VersionUtils.isFeatureSupported(context, "8.5")) {
    // Feature requires AA 8.5+
    enableAdvancedFeature()
}
```

### 5. Global Feature Toggle

```kotlin
// Check if AADisplay is enabled via Settings.Global
if (!VersionUtils.isAadEnabled(context)) {
    log(TAG, "AADisplay disabled via settings, skipping hooks")
    return
}
```

To disable AADisplay on a device:
```bash
adb shell settings put global aad_display_enable 0
```

To re-enable:
```bash
adb shell settings put global aad_display_enable 1
```

## Probe Mode for Debugging

Enable probe mode to capture detailed logs about all reflection attempts:

```kotlin
// Enable probe mode
ProbeLogger.setProbeMode(true)

// Log environment info
ProbeLogger.logEnvironmentInfo(context)

// Your reflection code here...
// All attempts will be logged automatically when using ReflectUtils

// Get summary of what worked and what failed
val summary = ProbeLogger.getHistorySummary()
log(TAG, summary)

// Disable probe mode when done
ProbeLogger.setProbeMode(false)
```

## Integration with Existing Hooks

### Example: Safe Hook Installation

```kotlin
override fun loadDexClass(bridge: DexKitBridge, lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        val classes = bridge.findClass {
            // Your search criteria
        }

        if (classes.isEmpty()) {
            ProbeLogger.logClassLoad(
                className = "TargetClass",
                success = false,
                error = NoSuchMethodException("Class not found")
            )
            throw NoSuchMethodException("Target class not found")
        }

        // Successfully found class
        ProbeLogger.logClassLoad(
            className = classes[0].name,
            success = true
        )

        // Continue with hook setup...
    } catch (e: Throwable) {
        log(tagName, "Failed to load dex class: ${e.message}", e)
        throw e
    }
}
```

### Example: Conditional Hook Based on Version

```kotlin
override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
    val context = EzXHelperInit.appContext

    // Check if feature should be enabled
    if (!VersionUtils.isAadEnabled(context)) {
        log(tagName, "AAD disabled, skipping hook")
        return
    }

    val aaVersion = VersionUtils.getAppVersion(context, VersionUtils.PACKAGE_ANDROID_AUTO)
    log(tagName, "Android Auto version: $aaVersion")

    // Version-specific hooks
    when {
        aaVersion?.startsWith("9.") == true -> {
            // Use new API for version 9.x
            hookNewApi()
        }
        aaVersion?.startsWith("8.") == true -> {
            // Use legacy API for version 8.x
            hookLegacyApi()
        }
        else -> {
            log(tagName, "Unsupported AA version: $aaVersion")
        }
    }
}
```

## Defensive Initialization

The main AndroidAuoHook.init() has been wrapped with defensive try-catch:

```kotlin
override fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
    try {
        // All initialization code
        // Individual hooks also have try-catch for isolation
    } catch (t: Throwable) {
        log(tagName, "AndroidAuoHook.init failed, disabling AAD hooks: ${t.message}", t)
        isAadInitFailed = true
    }
}
```

This ensures that:
- A failure in one hook doesn't crash the entire app
- Subsequent hook attempts are skipped if init fails
- Detailed error information is logged for debugging

## Best Practices

1. **Always use Optional-based API** - Forces you to handle missing classes/methods
2. **Log probe info during development** - Enable ProbeLogger to understand what's failing
3. **Add version checks** - Gate features based on Android Auto version
4. **Provide fallbacks** - Have alternative implementations for missing features
5. **Use global toggle** - Allow disabling AAD on problematic devices
6. **Wrap individual hooks** - Each hook should have its own try-catch
7. **Log environment info** - Capture device/version info when issues occur

## Troubleshooting

### Class Not Found
```kotlin
// Enable detailed logging
ProbeLogger.setProbeMode(true)
ProbeLogger.logEnvironmentInfo(context)

// Try loading the class
val result = ReflectUtils.safeForName("com.example.MissingClass", null)
if (!result.isPresent) {
    // Check probe history for details
    log(TAG, ProbeLogger.getHistorySummary())
}
```

### Method Not Found
```kotlin
// Log the attempt
val method = ReflectUtils.safeGetMethod(cls, "missingMethod", String::class.java)
if (!method.isPresent) {
    // Try alternative method names
    val alternatives = listOf("missingMethod", "alternativeMethod", "legacyMethod")
    for (name in alternatives) {
        val alt = ReflectUtils.safeGetMethod(cls, name, String::class.java)
        if (alt.isPresent) {
            log(TAG, "Found alternative method: $name")
            // Use this method instead
            break
        }
    }
}
```

### Version-Specific Issues
```kotlin
// Log detailed version info
VersionUtils.logDebugInfo(context)

// Check specific version
val version = VersionUtils.getAppVersion(context, VersionUtils.PACKAGE_ANDROID_AUTO)
log(TAG, "Current AA version: $version")

// Adjust behavior based on version
if (version != null) {
    when {
        version.startsWith("9.") -> useNewImplementation()
        version.startsWith("8.") -> useLegacyImplementation()
        else -> useGenericFallback()
    }
}
```

## Migration Checklist

When updating existing code to use safe reflection:

- [ ] Replace `Class.forName()` with `ReflectUtils.safeForName()`
- [ ] Replace `getMethod()` with `ReflectUtils.safeGetMethod()`
- [ ] Replace `invoke()` with `ReflectUtils.safeInvoke()`
- [ ] Replace `DexClassLoader` creation with `ReflectUtils.safeLoadDex()`
- [ ] Add version checks for version-specific features
- [ ] Add fallback implementations for missing features
- [ ] Wrap initialization code in try-catch
- [ ] Add probe logging for debugging
- [ ] Test on multiple Android Auto versions
- [ ] Test with AAD disabled via Settings.Global

## Example: Complete Safe Hook

```kotlin
object ExampleSafeHook : AaHook() {
    override val tagName: String = "AAD_ExampleSafeHook"

    private var featureMethod: Method? = null
    private var isFeatureAvailable = false

    override fun isSupportProcess(processName: String): Boolean {
        return processProjection == processName
    }

    override fun loadDexClass(bridge: DexKitBridge, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Enable probe mode during development
            if (BuildConfig.DEBUG) {
                ProbeLogger.setProbeMode(true)
            }

            val classes = bridge.findClass {
                // Search criteria
            }

            if (classes.isEmpty()) {
                ProbeLogger.logClassLoad("FeatureClass", success = false,
                    error = NoSuchMethodException("Class not found"))
                log(tagName, "Feature class not found, feature will be disabled")
                return
            }

            val cls = loadClass(classes[0].name)
            ProbeLogger.logClassLoad(cls.name, success = true)

            // Try to find the method
            val methodOpt = ReflectUtils.safeGetMethod(cls, "featureMethod", String::class.java)
            if (!methodOpt.isPresent) {
                ProbeLogger.logMethodLookup(cls.name, "featureMethod",
                    arrayOf(String::class.java), success = false,
                    error = NoSuchMethodException("Method not found"))
                log(tagName, "Feature method not found, feature will be disabled")
                return
            }

            featureMethod = methodOpt.get()
            isFeatureAvailable = true
            ProbeLogger.logMethodLookup(cls.name, "featureMethod",
                arrayOf(String::class.java), success = true)

        } catch (e: Throwable) {
            log(tagName, "Failed to load feature class: ${e.message}", e)
            isFeatureAvailable = false
        }
    }

    override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val context = EzXHelperInit.appContext

            // Check global toggle
            if (!VersionUtils.isAadEnabled(context)) {
                log(tagName, "AAD disabled via settings")
                return
            }

            // Check version compatibility
            val aaVersion = VersionUtils.getAppVersion(context, VersionUtils.PACKAGE_ANDROID_AUTO)
            log(tagName, "Android Auto version: $aaVersion")

            if (!isFeatureAvailable) {
                log(tagName, "Feature not available, using fallback")
                installFallbackHook()
                return
            }

            // Install the hook
            findMethod(SomeClass::class.java) {
                name == "targetMethod"
            }.hookBefore { param ->
                try {
                    // Use the feature method safely
                    ReflectUtils.safeInvoke(featureMethod, null, "arg").ifPresent { result ->
                        // Handle result
                        log(tagName, "Feature invoked successfully: $result")
                    }
                } catch (e: Throwable) {
                    log(tagName, "Feature invocation failed: ${e.message}", e)
                    ProbeLogger.logMethodInvoke("featureMethod", success = false, error = e)
                }
            }

            ProbeLogger.logHookInstall(tagName, "SomeClass", "targetMethod", success = true)

        } catch (e: Throwable) {
            log(tagName, "Hook installation failed: ${e.message}", e)
            ProbeLogger.logHookInstall(tagName, "SomeClass", "targetMethod",
                success = false, error = e)
        }
    }

    private fun installFallbackHook() {
        // Fallback implementation for when feature is not available
        log(tagName, "Installing fallback hook")
    }
}
```

## Summary

The safe reflection utilities provide:

✅ **Crash prevention** - No more app crashes from missing classes/methods
✅ **Better debugging** - Detailed logs show exactly what's failing
✅ **Version compatibility** - Easy to support multiple Android Auto versions
✅ **Feature toggles** - Disable problematic features on specific devices
✅ **Graceful degradation** - Fallback to alternative implementations
✅ **Probe mode** - Comprehensive logging for development and debugging

Use these utilities consistently across all hooks to make AADisplay more stable and maintainable.
