package com.example.meduza.core.utils

import android.app.ActivityManager
import android.content.Context

object DevicePerformanceManager {

    /**
     * Identifies if the current device is a "low-end" device.
     * Criteria for low-end:
     * - Less than 4GB Total RAM
     * - Or ActivityManager reports `isLowRamDevice()`
     * - Or less than 4 CPU cores
     */
    fun isLowEndDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        // Android's native low RAM flag
        if (activityManager.isLowRamDevice) return true
        
        // Check Total RAM
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        
        if (totalRamGb < 3.5) return true // Devices with 3GB or 2GB RAM
        
        // Check CPU Cores
        val cores = Runtime.getRuntime().availableProcessors()
        if (cores <= 4) return true

        return false
    }

    /**
     * Adaptive values for the app based on device capabilities.
     */
    fun getAdaptiveCacheLimit(context: Context): Long {
        return if (isLowEndDevice(context)) {
            50L * 1024 * 1024 // 50 MB
        } else {
            300L * 1024 * 1024 // 300 MB
        }
    }
    
    fun getAdaptiveThumbnailUrl(url: String?, isLowEnd: Boolean): String? {
        if (url == null) return null
        return if (isLowEnd) {
            // Replace massive 540p images with highly compressed 120p images to save RAM & Bandwidth
            url.replace(Regex("=w\\d+-h\\d+.*"), "=w120-h120-l90-rj")
        } else {
            url.replace(Regex("=w\\d+-h\\d+.*"), "=w540-h540-l90-rj")
        }
    }
}
