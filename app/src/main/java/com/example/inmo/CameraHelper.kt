package com.example.inmo

import android.hardware.Camera
import android.util.Log

@Suppress("DEPRECATION")
object CameraHelper {
    
    private const val TAG = "CameraHelper"
    
    /**
     * 获取可用摄像头数量
     */
    fun getNumberOfCameras(): Int {
        return Camera.getNumberOfCameras()
    }
    
    /**
     * 获取摄像头信息
     */
    fun getCameraInfo(cameraId: Int): Camera.CameraInfo? {
        return try {
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, cameraInfo)
            cameraInfo
        } catch (e: Exception) {
            Log.e(TAG, "获取摄像头信息失败: ${e.message}")
            null
        }
    }
    
    /**
     * 检查摄像头是否可用
     */
    fun isCameraAvailable(cameraId: Int): Boolean {
        return try {
            val camera = Camera.open(cameraId)
            camera.release()
            true
        } catch (e: Exception) {
            Log.e(TAG, "摄像头 $cameraId 不可用: ${e.message}")
            false
        }
    }
    
    /**
     * 获取最佳预览尺寸
     */
    fun getBestPreviewSize(
        supportedSizes: List<Camera.Size>,
        targetWidth: Int,
        targetHeight: Int
    ): Camera.Size? {
        if (supportedSizes.isEmpty()) return null
        
        val targetRatio = targetWidth.toDouble() / targetHeight
        var bestSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE
        
        // 寻找最接近目标比例的尺寸
        for (size in supportedSizes) {
            val ratio = size.width.toDouble() / size.height
            val diff = Math.abs(ratio - targetRatio)
            
            if (diff < minDiff) {
                minDiff = diff
                bestSize = size
            }
        }
        
        return bestSize ?: supportedSizes[0]
    }
    
    /**
     * 打印摄像头支持的功能
     */
    fun logCameraCapabilities(camera: Camera) {
        try {
            val parameters = camera.parameters
            
            Log.d(TAG, "=== 摄像头功能信息 ===")
            
            // 预览尺寸
            val previewSizes = parameters.supportedPreviewSizes
            Log.d(TAG, "支持的预览尺寸:")
            previewSizes.forEach { size ->
                Log.d(TAG, "  ${size.width}x${size.height}")
            }
            
            // 图片尺寸
            val pictureSizes = parameters.supportedPictureSizes
            Log.d(TAG, "支持的图片尺寸:")
            pictureSizes.forEach { size ->
                Log.d(TAG, "  ${size.width}x${size.height}")
            }
            
            // 对焦模式
            val focusModes = parameters.supportedFocusModes
            Log.d(TAG, "支持的对焦模式: $focusModes")
            
            // 闪光灯模式
            val flashModes = parameters.supportedFlashModes
            Log.d(TAG, "支持的闪光灯模式: $flashModes")
            
            // 场景模式
            val sceneModes = parameters.supportedSceneModes
            Log.d(TAG, "支持的场景模式: $sceneModes")
            
            // 白平衡
            val whiteBalanceModes = parameters.supportedWhiteBalance
            Log.d(TAG, "支持的白平衡模式: $whiteBalanceModes")
            
            Log.d(TAG, "=== 摄像头功能信息结束 ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "获取摄像头功能信息失败: ${e.message}")
        }
    }
}