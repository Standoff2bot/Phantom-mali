package com.winlator.cmod.renderer;

import android.util.Log;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Texture pool to eliminate per-frame GPU buffer allocations
 * Significantly reduces memory allocation overhead for Mali GPUs
 */
public class GPUImagePool {
    private static final String TAG = "GPUImagePool";
    
    // Pool configuration
    private static final int MAX_POOL_SIZE = 32;
    private static final int INITIAL_POOL_SIZE = 8;
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds
    private static final long TEXTURE_TIMEOUT_MS = 60000; // 1 minute
    
    // Size-based pools (width << 16 | height)
    private static final Map<Integer, Queue<PooledGPUImage>> pools = new HashMap<>();
    private static long lastCleanupTime = 0;
    private static int totalAllocations = 0;
    private static int poolHits = 0;
    private static int poolMisses = 0;
    
    /**
     * Pooled GPUImage wrapper with timestamp
     */
    private static class PooledGPUImage {
        GPUImage image;
        long lastUsedTime;
        
        PooledGPUImage(GPUImage image) {
            this.image = image;
            this.lastUsedTime = System.currentTimeMillis();
        }
        
        void updateTimestamp() {
            lastUsedTime = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - lastUsedTime) > TEXTURE_TIMEOUT_MS;
        }
    }
    
    /**
     * Get size key for pooling
     */
    private static int getSizeKey(short width, short height) {
        return (width << 16) | (height & 0xFFFF);
    }
    
    /**
     * Acquire GPUImage from pool or create new one
     */
    public static GPUImage acquire(short width, short height) {
        int sizeKey = getSizeKey(width, height);
        
        synchronized (pools) {
            Queue<PooledGPUImage> pool = pools.get(sizeKey);
            
            if (pool != null && !pool.isEmpty()) {
                PooledGPUImage pooled = pool.poll();
                if (pooled != null && pooled.image != null) {
                    pooled.updateTimestamp();
                    poolHits++;
                    Log.d(TAG, "Pool HIT: " + width + "x" + height + " (pool size: " + pool.size() + ")");
                    return pooled.image;
                }
            }
            
            // Pool miss - create new texture
            poolMisses++;
            totalAllocations++;
            Log.d(TAG, "Pool MISS: " + width + "x" + height + " (creating new, total: " + totalAllocations + ")");
            return new GPUImage(width, height);
        }
    }
    
    /**
     * Release GPUImage back to pool
     */
    public static void release(GPUImage image) {
        if (image == null || !image.isAllocated()) return;
        
        // Get size from texture
        short width = image.getWidth();
        short height = image.getHeight();
        int sizeKey = getSizeKey(width, height);
        
        synchronized (pools) {
            Queue<PooledGPUImage> pool = pools.get(sizeKey);
            
            if (pool == null) {
                pool = new ArrayDeque<>(INITIAL_POOL_SIZE);
                pools.put(sizeKey, pool);
            }
            
            if (pool.size() < MAX_POOL_SIZE) {
                pool.offer(new PooledGPUImage(image));
                Log.d(TAG, "Released to pool: " + width + "x" + height + " (pool size: " + pool.size() + ")");
            } else {
                // Pool full - destroy texture
                image.destroy();
                Log.d(TAG, "Pool full, destroyed: " + width + "x" + height);
            }
            
            // Periodic cleanup
            long now = System.currentTimeMillis();
            if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                cleanup();
                lastCleanupTime = now;
            }
        }
    }
    
    /**
     * Pre-warm pool with common sizes
     */
    public static void prewarm(short[] commonSizes) {
        synchronized (pools) {
            Log.i(TAG, "Pre-warming pool with " + (commonSizes.length / 2) + " size(s)");
            
            for (int i = 0; i < commonSizes.length; i += 2) {
                short width = commonSizes[i];
                short height = commonSizes[i + 1];
                int sizeKey = getSizeKey(width, height);
                
                Queue<PooledGPUImage> pool = pools.get(sizeKey);
                if (pool == null) {
                    pool = new ArrayDeque<>(INITIAL_POOL_SIZE);
                    pools.put(sizeKey, pool);
                }
                
                // Allocate initial pool entries
                for (int j = 0; j < INITIAL_POOL_SIZE && pool.size() < MAX_POOL_SIZE; j++) {
                    GPUImage image = new GPUImage(width, height);
                    pool.offer(new PooledGPUImage(image));
                    totalAllocations++;
                }
                
                Log.d(TAG, "Pre-warmed: " + width + "x" + height + " (" + pool.size() + " entries)");
            }
        }
    }
    
    /**
     * Clean up expired textures from all pools
     */
    private static void cleanup() {
        int freedCount = 0;
        
        for (Map.Entry<Integer, Queue<PooledGPUImage>> entry : pools.entrySet()) {
            Queue<PooledGPUImage> pool = entry.getValue();
            Queue<PooledGPUImage> newPool = new ArrayDeque<>();
            
            while (!pool.isEmpty()) {
                PooledGPUImage pooled = pool.poll();
                if (pooled != null && !pooled.isExpired()) {
                    newPool.offer(pooled);
                } else if (pooled != null) {
                    pooled.image.destroy();
                    freedCount++;
                }
            }
            
            if (!newPool.isEmpty()) {
                pools.put(entry.getKey(), newPool);
            } else {
                pools.remove(entry.getKey());
            }
        }
        
        if (freedCount > 0) {
            Log.i(TAG, "Cleanup: freed " + freedCount + " expired textures");
        }
    }
    
    /**
     * Clear entire pool (destroy all textures)
     */
    public static void clear() {
        synchronized (pools) {
            int destroyedCount = 0;
            
            for (Queue<PooledGPUImage> pool : pools.values()) {
                while (!pool.isEmpty()) {
                    PooledGPUImage pooled = pool.poll();
                    if (pooled != null && pooled.image != null) {
                        pooled.image.destroy();
                        destroyedCount++;
                    }
                }
            }
            
            pools.clear();
            Log.i(TAG, "Cleared pool: destroyed " + destroyedCount + " textures");
            
            // Reset stats
            totalAllocations = 0;
            poolHits = 0;
            poolMisses = 0;
        }
    }
    
    /**
     * Get pool statistics
     */
    public static String getStats() {
        synchronized (pools) {
            int totalPooled = 0;
            for (Queue<PooledGPUImage> pool : pools.values()) {
                totalPooled += pool.size();
            }
            
            float hitRate = (poolHits + poolMisses) > 0 ? 
                (poolHits * 100.0f) / (poolHits + poolMisses) : 0;
            
            return String.format("Pool Stats: %d pooled, %d sizes, %.1f%% hit rate (%d hits, %d misses, %d total allocs)",
                totalPooled, pools.size(), hitRate, poolHits, poolMisses, totalAllocations);
        }
    }
    
    /**
     * Force immediate cleanup
     */
    public static void forceCleanup() {
        synchronized (pools) {
            cleanup();
            lastCleanupTime = System.currentTimeMillis();
        }
    }
}
