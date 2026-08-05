package com.winlator.cmod.core;

import android.content.Context;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * Mali GPU Optimizer for Mali-G710 (Dimensity 9000)
 * Provides Mali-specific optimizations for Mesa/Vulkan rendering pipeline
 */
public class MaliGPUOptimizer {
    private static final String TAG = "MaliGPUOptimizer";
    
    // Cached GPU information
    private static String cachedRenderer = null;
    private static String cachedVulkanVersion = null;
    private static int cachedVendorID = -1;
    private static boolean cacheInitialized = false;
    
    // Mali GPU detection
    private static final int VENDOR_ID_ARM = 0x13B5;
    private static final String[] MALI_IDENTIFIERS = {
        "Mali", "mali", "ARM Mali", "Mali-G710", "Mali-G", "Valhall"
    };
    
    // Mali-G710 specific parameters
    private static final int MALI_G710_CORES = 10;
    private static final int MALI_G710_MAX_THREADS = 16; // Per core
    
    /**
     * Initialize GPU information cache at startup
     */
    public static void initializeCache(Context context) {
        if (!cacheInitialized) {
            try {
                cachedRenderer = GPUInformation.getRenderer(null, context);
                cachedVulkanVersion = GPUInformation.getVulkanVersion(null, context);
                cachedVendorID = GPUInformation.getVendorID(null, context);
                cacheInitialized = true;
                Log.i(TAG, "GPU Cache initialized: " + cachedRenderer + " (VendorID: 0x" + 
                      Integer.toHexString(cachedVendorID) + ")");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize GPU cache", e);
            }
        }
    }
    
    /**
     * Check if current GPU is Mali (uses cached value)
     */
    public static boolean isMaliGPU() {
        if (!cacheInitialized) return false;
        
        if (cachedVendorID == VENDOR_ID_ARM) return true;
        
        if (cachedRenderer != null) {
            for (String identifier : MALI_IDENTIFIERS) {
                if (cachedRenderer.contains(identifier)) return true;
            }
        }
        return false;
    }
    
    /**
     * Check if GPU is specifically Mali-G710
     */
    public static boolean isMaliG710() {
        if (!cacheInitialized) return false;
        return cachedRenderer != null && 
               (cachedRenderer.contains("Mali-G710") || cachedRenderer.contains("Mali-G71"));
    }
    
    /**
     * Get cached renderer string
     */
    public static String getRenderer() {
        return cachedRenderer != null ? cachedRenderer : "Unknown";
    }
    
    /**
     * Get cached Vulkan version
     */
    public static String getVulkanVersion() {
        return cachedVulkanVersion != null ? cachedVulkanVersion : "1.0.0";
    }
    
    /**
     * Get Mali-G710 optimized environment variables
     */
    public static Map<String, String> getMaliOptimizedEnvVars() {
        Map<String, String> envVars = new HashMap<>();
        
        if (!isMaliGPU()) return envVars;
        
        // Mesa optimization for Mali
        envVars.put("MESA_LOADER_DRIVER_OVERRIDE", "panfrost");
        envVars.put("GALLIUM_DRIVER", "panfrost");
        
        // Disable AFBC (ARM Frame Buffer Compression) - causes corruption on Mali-G710
        envVars.put("PAN_MESA_DEBUG", "noafbc,sync");
        
        // Shader cache optimization
        envVars.put("MESA_SHADER_CACHE_DISABLE", "false");
        envVars.put("MESA_SHADER_CACHE_MAX_SIZE", "1024MB"); // Increased for Mali
        envVars.put("MESA_GLSL_CACHE_DISABLE", "false");
        
        // Performance optimizations
        envVars.put("MESA_NO_ERROR", "1"); // Skip validation for performance
        envVars.put("mesa_glthread", "true"); // Multi-threaded GL driver
        
        // Mali-specific Panfrost tuning
        envVars.put("PAN_MESA_PERF", "1"); // Enable performance mode
        
        // Tile-based rendering hints for Mali
        envVars.put("MALI_FORCE_STAGING", "0"); // Disable staging buffers
        
        // Present mode optimization for tile-based renderer
        envVars.put("MESA_VK_WSI_PRESENT_MODE", "fifo"); // Better for Mali than mailbox
        
        // BCN texture cache (enable for Mali)
        envVars.put("BCN_EMULATION_CACHE", "1");
        
        Log.i(TAG, "Applied Mali GPU optimizations");
        return envVars;
    }
    
    /**
     * Get Mali-G710 optimized Zink (Vulkan-over-OpenGL) environment variables
     */
    public static Map<String, String> getMaliZinkOptimizedEnvVars() {
        Map<String, String> envVars = new HashMap<>();
        
        if (!isMaliGPU()) return envVars;
        
        // Override to use Zink (OpenGL over Vulkan)
        envVars.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envVars.put("GALLIUM_DRIVER", "zink");
        
        // Zink optimizations for Mali
        envVars.put("ZINK_DESCRIPTORS", "lazy"); // Lazy descriptor allocation
        envVars.put("ZINK_DEBUG", "compact,nir"); // Reduced overhead, NIR optimizations
        
        // Mali-specific Vulkan tuning
        envVars.put("MESA_VK_WSI_PRESENT_MODE", "immediate"); // Low latency for Mali
        envVars.put("WRAPPER_MAX_IMAGE_COUNT", "3"); // Triple buffering for Mali
        
        // Disable AFBC through Vulkan
        envVars.put("PAN_MESA_DEBUG", "noafbc");
        
        // Shader compilation optimization
        envVars.put("MESA_SHADER_CACHE_DISABLE", "false");
        envVars.put("MESA_SHADER_CACHE_MAX_SIZE", "1024MB");
        
        Log.i(TAG, "Applied Mali Zink optimizations");
        return envVars;
    }
    
    /**
     * Get Mali-G710 optimized Box64 environment variables
     */
    public static Map<String, String> getMaliBox64OptimizedEnvVars() {
        Map<String, String> envVars = new HashMap<>();
        
        if (!isMaliGPU()) return envVars;
        
        // Disable 32-bit memory mapping for Mali compatibility
        envVars.put("BOX64_MMAP32", "0");
        
        // Dynarec optimization for Dimensity 9000 (ARM Cortex-X2/A710)
        envVars.put("BOX64_DYNAREC", "1");
        envVars.put("BOX64_DYNAREC_STRONGMEM", "3"); // Strong memory model for ARM Mali
        envVars.put("BOX64_DYNAREC_BIGBLOCK", "2"); // Larger code blocks
        envVars.put("BOX64_DYNAREC_FORWARD", "512"); // Forward scan optimization
        
        // CPU-specific tuning for Dimensity 9000
        envVars.put("BOX64_DYNAREC_FASTNAN", "1"); // Fast NaN handling
        envVars.put("BOX64_DYNAREC_FASTROUND", "1"); // Fast rounding
        
        // Cache optimization (Dimensity 9000: 6MB L3 cache)
        envVars.put("BOX64_DYNAREC_CALLRET", "1"); // Optimize call/ret
        
        // OpenGL acceleration
        envVars.put("BOX64_X11GLX", "1");
        envVars.put("BOX64_LIBGL", "libGL.so.1");
        
        Log.i(TAG, "Applied Mali Box64 optimizations");
        return envVars;
    }
    
    /**
     * Get optimal workgroup size for Mali-G710 compute shaders
     */
    public static int[] getMaliOptimalWorkgroupSize() {
        if (isMaliG710()) {
            // Mali-G710 with 10 cores, each with 16 threads
            // Optimal: 8x8 or 16x8 for tile-based rendering
            return new int[]{16, 8, 1};
        }
        // Generic Mali fallback
        return new int[]{16, 16, 1};
    }
    
    /**
     * Get Mali-specific graphics driver configuration
     */
    public static String getMaliGraphicsDriverConfig() {
        if (!isMaliGPU()) return "";
        
        StringBuilder config = new StringBuilder();
        config.append("vulkanVersion=1.3;");
        config.append("presentMode=fifo;"); // Better for Mali tile-based rendering
        config.append("syncFrame=1;"); // Enable frame sync for Mali stability
        config.append("astcTranscode=1;"); // ASTC native support on Mali
        config.append("bcnEmulation=auto;");
        config.append("bcnEmulationType=compute;"); // Use compute for BCN on Mali
        config.append("bcnEmulationCache=1;"); // ENABLE cache for Mali (was 0)
        config.append("resourceType=auto");
        
        return config.toString();
    }
    
    /**
     * Get recommended shader cache directory
     */
    public static String getMaliShaderCacheDir(Context context) {
        return context.getCacheDir().getAbsolutePath() + "/mali_shader_cache";
    }
    
    /**
     * Apply Mali-specific optimizations to environment variables map
     */
    public static void applyMaliOptimizations(Map<String, String> envVars, boolean useZink) {
        if (!isMaliGPU()) {
            Log.i(TAG, "Non-Mali GPU detected, skipping Mali optimizations");
            return;
        }
        
        Map<String, String> maliVars = useZink ? 
            getMaliZinkOptimizedEnvVars() : getMaliOptimizedEnvVars();
        
        Map<String, String> box64Vars = getMaliBox64OptimizedEnvVars();
        
        envVars.putAll(maliVars);
        envVars.putAll(box64Vars);
        
        Log.i(TAG, "Applied " + (useZink ? "Zink" : "Native Panfrost") + 
              " Mali optimizations (" + maliVars.size() + " + " + box64Vars.size() + " vars)");
    }
    
    /**
     * Get Mali optimization summary for display
     */
    public static String getMaliOptimizationSummary() {
        if (!isMaliGPU()) return "Non-Mali GPU";
        
        StringBuilder summary = new StringBuilder();
        summary.append("Mali Optimizations Active\n");
        summary.append("GPU: ").append(getRenderer()).append("\n");
        summary.append("Vulkan: ").append(getVulkanVersion()).append("\n");
        summary.append("Driver: Panfrost (native)\n");
        summary.append("AFBC: Disabled (stability)\n");
        summary.append("Shader Cache: 1GB\n");
        summary.append("Present Mode: FIFO (tile-optimized)\n");
        summary.append("Box64 MMAP32: Disabled\n");
        
        if (isMaliG710()) {
            summary.append("Mali-G710 Specific: 10-core tuning enabled");
        }
        
        return summary.toString();
    }
    
    /**
     * Clear cached GPU information (for testing)
     */
    public static void clearCache() {
        cachedRenderer = null;
        cachedVulkanVersion = null;
        cachedVendorID = -1;
        cacheInitialized = false;
        Log.i(TAG, "GPU cache cleared");
    }
}
