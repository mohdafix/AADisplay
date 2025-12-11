package io.github.nitsuya.aa.display.xposed.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Optional;

import dalvik.system.DexClassLoader;

/**
 * Safe reflection and dex-loading utility to prevent crashes from missing classes/methods.
 * Uses Optional to force handling of absence and provides comprehensive logging.
 */
public final class ReflectUtils {
    private static final String TAG = "AADisplayReflect";

    private ReflectUtils() {}

    /**
     * Safely load a class by name with optional custom ClassLoader.
     *
     * @param className The fully qualified class name to load
     * @param loader Optional ClassLoader (can be null to use default)
     * @return Optional containing the Class if found, empty otherwise
     */
    public static Optional<Class<?>> safeForName(String className, ClassLoader loader) {
        try {
            Class<?> clazz;
            if (loader != null) {
                clazz = Class.forName(className, false, loader);
            } else {
                clazz = Class.forName(className);
            }
            // Log success if probe mode is enabled (via Kotlin ProbeLogger)
            logClassLoadSuccess(className, null);
            return Optional.of(clazz);
        } catch (Throwable t) {
            Log.w(TAG, "Class not found: " + className + " - " + t.getMessage());
            // Log failure for probe tracking
            logClassLoadFailure(className, null, t);
            return Optional.empty();
        }
    }

    /**
     * Safely get a public method from a class.
     *
     * @param cls The class to search for the method
     * @param name The method name
     * @param params The parameter types
     * @return Optional containing the Method if found, empty otherwise
     */
    public static Optional<Method> safeGetMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            Method method = cls.getMethod(name, params);
            logMethodLookupSuccess(cls.getName(), name, params);
            return Optional.of(method);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Method not found: " + cls.getName() + "#" + name);
            logMethodLookupFailure(cls.getName(), name, params, e);
            return Optional.empty();
        } catch (Throwable t) {
            Log.w(TAG, "safeGetMethod error: " + t.getMessage());
            logMethodLookupFailure(cls.getName(), name, params, t);
            return Optional.empty();
        }
    }

    /**
     * Safely invoke a method with error handling.
     *
     * @param m The method to invoke
     * @param instance The instance to invoke on (null for static methods)
     * @param args The method arguments
     * @return Optional containing the result if successful, empty otherwise
     */
    public static Optional<Object> safeInvoke(Method m, Object instance, Object... args) {
        try {
            if (m == null) {
                logMethodInvokeFailure("null", new NullPointerException("Method is null"));
                return Optional.empty();
            }
            Object result = m.invoke(instance, args);
            logMethodInvokeSuccess(m.getName());
            return Optional.ofNullable(result);
        } catch (Throwable t) {
            Log.w(TAG, "invoke failed: " + (m == null ? "null method" : m.getName()) + " - " + t.getMessage());
            logMethodInvokeFailure(m == null ? "null" : m.getName(), t);
            return Optional.empty();
        }
    }

    /**
     * Safely load a DEX file with proper error handling and logging.
     *
     * @param dexPath Path to the DEX file
     * @param context Application context for cache directory
     * @return ClassLoader if successful, null otherwise
     */
    public static ClassLoader safeLoadDex(String dexPath, Context context) {
        try {
            // use optimized dir inside app cache
            File optDir = new File(context.getCacheDir(), "aad_opt");
            if (!optDir.exists()) optDir.mkdirs();
            DexClassLoader loader = new DexClassLoader(dexPath, optDir.getAbsolutePath(), null, context.getClassLoader());
            Log.i(TAG, "DexClassLoader created for " + dexPath);
            logDexLoadSuccess(dexPath);
            return loader;
        } catch (Throwable t) {
            Log.w(TAG, "Dex load failed: " + t.getMessage());
            logDexLoadFailure(dexPath, t);
            return null;
        }
    }

    /**
     * Load a class from a DEX file with comprehensive error handling.
     *
     * @param dexPath Path to the DEX file
     * @param className Fully qualified class name to load
     * @param context Application context
     * @return The loaded Class if successful, null otherwise
     */
    public static Class<?> loadDexClassSafe(String dexPath, String className, Context context) {
        Log.i(TAG, "Trying to load " + className + " from " + dexPath + " on Android " + Build.VERSION.RELEASE);

        ClassLoader loader = safeLoadDex(dexPath, context);
        if (loader == null) {
            Log.w(TAG, "Failed to create DexClassLoader for " + dexPath);
            return null;
        }

        Optional<Class<?>> maybe = safeForName(className, loader);
        if (!maybe.isPresent()) {
            // possibly dex loaded but class renamed; try scanning nearby candidate names (optional)
            Log.w(TAG, "Class " + className + " not present in dex " + dexPath);
            return null;
        }

        Log.i(TAG, "Successfully loaded class: " + className);
        return maybe.get();
    }

    /**
     * Get the version of a target application package.
     *
     * @param context Application context
     * @param packageName Package name to query
     * @return Version string if found, null otherwise
     */
    public static String getTargetAppVersion(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to get version for package: " + packageName + " - " + t.getMessage());
            return null;
        }
    }

    /**
     * Check if AADisplay hooks are enabled via Settings.Global.
     *
     * @param context Application context
     * @return true if enabled (default), false if disabled
     */
    public static boolean isAadEnabled(Context context) {
        try {
            return android.provider.Settings.Global.getInt(
                context.getContentResolver(),
                "aad_display_enable",
                1
            ) == 1;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read aad_display_enable setting, defaulting to enabled: " + t.getMessage());
            return true; // default to enabled if setting can't be read
        }
    }

    /**
     * Log detailed probe information for debugging reflection issues.
     *
     * @param className Class name being attempted
     * @param dexPath DEX path if applicable
     * @param methodName Method name if applicable
     * @param error The error that occurred
     */
    public static void logProbeInfo(String className, String dexPath, String methodName, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AADisplay Reflection Probe ===\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Class: ").append(className).append("\n");
        if (dexPath != null) {
            sb.append("DEX Path: ").append(dexPath).append("\n");
        }
        if (methodName != null) {
            sb.append("Method: ").append(methodName).append("\n");
        }
        if (error != null) {
            sb.append("Error: ").append(error.getClass().getSimpleName()).append(" - ").append(error.getMessage()).append("\n");
            sb.append("Stack trace: ");
            for (StackTraceElement element : error.getStackTrace()) {
                sb.append("\n  at ").append(element.toString());
            }
        }
        sb.append("\n=================================");
        Log.w(TAG, sb.toString());
    }

    // Helper methods to integrate with ProbeLogger (Kotlin)
    // These are called via reflection to avoid hard dependency on Kotlin code

    private static void logClassLoadSuccess(String className, String dexPath) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logClassLoad", String.class, String.class, boolean.class, Throwable.class);
            method.invoke(null, className, dexPath, true, null);
        } catch (Throwable ignored) {
            // ProbeLogger not available or probe mode disabled
        }
    }

    private static void logClassLoadFailure(String className, String dexPath, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logClassLoad", String.class, String.class, boolean.class, Throwable.class);
            method.invoke(null, className, dexPath, false, error);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logMethodLookupSuccess(String className, String methodName, Class<?>[] params) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodLookup", String.class, String.class, Class[].class, boolean.class, Throwable.class);
            method.invoke(null, className, methodName, params, true, null);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logMethodLookupFailure(String className, String methodName, Class<?>[] params, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodLookup", String.class, String.class, Class[].class, boolean.class, Throwable.class);
            method.invoke(null, className, methodName, params, false, error);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logMethodInvokeSuccess(String methodName) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodInvoke", String.class, boolean.class, Throwable.class);
            method.invoke(null, methodName, true, null);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logMethodInvokeFailure(String methodName, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logMethodInvoke", String.class, boolean.class, Throwable.class);
            method.invoke(null, methodName, false, error);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logDexLoadSuccess(String dexPath) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logDexLoad", String.class, boolean.class, Throwable.class);
            method.invoke(null, dexPath, true, null);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }

    private static void logDexLoadFailure(String dexPath, Throwable error) {
        try {
            Class<?> probeLogger = Class.forName("io.github.nitsuya.aa.display.xposed.util.ProbeLogger");
            Method method = probeLogger.getMethod("logDexLoad", String.class, boolean.class, Throwable.class);
            method.invoke(null, dexPath, false, error);
        } catch (Throwable ignored) {
            // ProbeLogger not available
        }
    }
}
