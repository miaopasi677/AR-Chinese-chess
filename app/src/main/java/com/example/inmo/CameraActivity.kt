package com.example.inmo

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback {
    
    private var camera: Camera? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var cameraStatusText: TextView? = null
    
    // INMO AIR3 RGB摄像头ID
    private var INMO_AIR3_RGB_CAMERA_ID = 0 // 普通摄像头
    private var currentCameraId = 0
    private var isPreviewRunning = false
    private var isPictureTaking = false
    
    companion object {
        private const val TAG = "CameraActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val STORAGE_PERMISSION_REQUEST_CODE = 101
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        
        // 初始化UI组件
        initViews()
        
        // 初始化SurfaceView
        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView?.holder
        surfaceHolder?.addCallback(this)
        
        // 检查摄像头权限
        if (checkCameraPermission()) {
            initCamera()
        } else {
            requestCameraPermission()
        }
    }
    
    private fun initViews() {
        cameraStatusText = findViewById(R.id.cameraStatus)
        
        // 拍照按钮
        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            capturePhoto()
        }
        
        // 切换摄像头按钮
        findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener {
            switchCamera()
        }
        
        // 关闭按钮
        findViewById<Button>(R.id.btnClose).setOnClickListener {
            finish()
        }
        
        updateCameraStatus()
    }
    
    private fun updateCameraStatus() {
        cameraStatusText?.text = "摄像头ID: $currentCameraId"
    }
    
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initCamera()
                } else {
                    Toast.makeText(this, "需要摄像头权限才能使用此功能", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
    
    private fun initCamera() {
        try {
            // 检查摄像头是否可用
            if (!CameraHelper.isCameraAvailable(INMO_AIR3_RGB_CAMERA_ID)) {
                Toast.makeText(this, "摄像头 $INMO_AIR3_RGB_CAMERA_ID 不可用", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // 打开普通摄像头
            camera = Camera.open(INMO_AIR3_RGB_CAMERA_ID)
            currentCameraId = INMO_AIR3_RGB_CAMERA_ID
            updateCameraStatus()
            
            // 打印摄像头功能信息
            camera?.let { CameraHelper.logCameraCapabilities(it) }
            
            Log.d(TAG, "摄像头打开成功，ID: $INMO_AIR3_RGB_CAMERA_ID")
            Log.d(TAG, "系统总摄像头数量: ${CameraHelper.getNumberOfCameras()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "打开摄像头失败: ${e.message}")
            Toast.makeText(this, "打开摄像头失败: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun switchCamera() {
        val numberOfCameras = CameraHelper.getNumberOfCameras()
        if (numberOfCameras > 1) {
            releaseCamera()
            currentCameraId = (currentCameraId + 1) % numberOfCameras
            
            // 检查新摄像头是否可用
            if (!CameraHelper.isCameraAvailable(currentCameraId)) {
                Toast.makeText(this, "摄像头 $currentCameraId 不可用", Toast.LENGTH_SHORT).show()
                // 回退到原来的摄像头
                currentCameraId = (currentCameraId - 1 + numberOfCameras) % numberOfCameras
                camera = Camera.open(currentCameraId)
                return
            }
            
            try {
                camera = Camera.open(currentCameraId)
                updateCameraStatus()
                startCameraPreview()
                Toast.makeText(this, "切换到摄像头 $currentCameraId", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "切换到摄像头ID: $currentCameraId")
                
                // 打印新摄像头的功能信息
                camera?.let { CameraHelper.logCameraCapabilities(it) }
                
            } catch (e: Exception) {
                Log.e(TAG, "切换摄像头失败: ${e.message}")
                Toast.makeText(this, "切换摄像头失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "只有一个摄像头可用", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun capturePhoto() {
        if (isPictureTaking) {
            Log.d(TAG, "正在拍照中，请稍候...")
            return
        }
        
        if (!isPreviewRunning) {
            Log.e(TAG, "预览未运行，无法拍照")
            Toast.makeText(this, "摄像头预览未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        
        camera?.let { cam ->
            try {
                isPictureTaking = true
                Log.d(TAG, "开始拍照...")
                
                // 先进行自动对焦（如果支持）
                val parameters = cam.parameters
                if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    cam.autoFocus { success, _ ->
                        Log.d(TAG, "自动对焦${if (success) "成功" else "失败"}")
                        takePictureInternal(cam)
                    }
                } else {
                    takePictureInternal(cam)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "拍照准备失败: ${e.message}")
                Toast.makeText(this, "拍照准备失败: ${e.message}", Toast.LENGTH_SHORT).show()
                isPictureTaking = false
            }
        } ?: run {
            Log.e(TAG, "摄像头未初始化")
            Toast.makeText(this, "摄像头未初始化", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun takePictureInternal(cam: Camera) {
        try {
            cam.takePicture(
                { 
                    Log.d(TAG, "快门声音") 
                },
                null,
                { data, _ ->
                    Log.d(TAG, "照片拍摄成功，大小: ${data.size} bytes")
                    
                    // 保存照片到文件
                    val savedFile = savePhotoToFile(data)
                    val message = if (savedFile != null) {
                        "照片已保存: ${savedFile.name}"
                    } else {
                        "照片拍摄成功 (${data.size} bytes)"
                    }
                    
                    Toast.makeText(this@CameraActivity, message, Toast.LENGTH_SHORT).show()
                    
                    // 重新开始预览
                    try {
                        cam.startPreview()
                        isPreviewRunning = true
                        Log.d(TAG, "拍照后预览重新开始")
                    } catch (e: Exception) {
                        Log.e(TAG, "拍照后重启预览失败: ${e.message}")
                    }
                    
                    isPictureTaking = false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "拍照失败: ${e.message}")
            Toast.makeText(this, "拍照失败: ${e.message}", Toast.LENGTH_SHORT).show()
            isPictureTaking = false
            
            // 尝试重新开始预览
            try {
                cam.startPreview()
                isPreviewRunning = true
            } catch (restartException: Exception) {
                Log.e(TAG, "拍照失败后重启预览失败: ${restartException.message}")
            }
        }
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface创建")
        startCameraPreview()
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface改变: ${width}x${height}")
        // 停止预览
        stopCameraPreview()
        // 重新开始预览
        startCameraPreview()
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface销毁")
        stopCameraPreview()
    }
    
    private fun startCameraPreview() {
        camera?.let { cam ->
            try {
                // 设置预览显示的Surface
                cam.setPreviewDisplay(surfaceHolder)
                
                // 设置摄像头参数
                val parameters = cam.parameters
                
                // 获取支持的预览尺寸和图片尺寸
                val supportedPreviewSizes = parameters.supportedPreviewSizes
                val supportedPictureSizes = parameters.supportedPictureSizes
                
                if (supportedPreviewSizes.isNotEmpty()) {
                    // 选择合适的预览尺寸
                    val previewSize = supportedPreviewSizes[0]
                    parameters.setPreviewSize(previewSize.width, previewSize.height)
                    Log.d(TAG, "设置预览尺寸: ${previewSize.width}x${previewSize.height}")
                }
                
                if (supportedPictureSizes.isNotEmpty()) {
                    // 设置图片尺寸（选择最大的）
                    val pictureSize = supportedPictureSizes.maxByOrNull { it.width * it.height }
                    pictureSize?.let {
                        parameters.setPictureSize(it.width, it.height)
                        Log.d(TAG, "设置图片尺寸: ${it.width}x${it.height}")
                    }
                }
                
                // 设置图片格式
                parameters.pictureFormat = android.graphics.ImageFormat.JPEG
                
                // 设置图片质量
                parameters.jpegQuality = 90
                
                // 设置自动对焦（如果支持）
                val focusModes = parameters.supportedFocusModes
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    Log.d(TAG, "设置连续对焦模式")
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                    Log.d(TAG, "设置自动对焦")
                }
                
                // 设置闪光灯模式（如果支持）
                val flashModes = parameters.supportedFlashModes
                if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                    parameters.flashMode = Camera.Parameters.FLASH_MODE_AUTO
                    Log.d(TAG, "设置自动闪光灯")
                }
                
                // 应用参数
                cam.parameters = parameters
                
                // 开始预览
                cam.startPreview()
                isPreviewRunning = true
                Log.d(TAG, "摄像头预览开始")
                
            } catch (e: IOException) {
                Log.e(TAG, "设置摄像头预览失败: ${e.message}")
                Toast.makeText(this, "摄像头预览失败", Toast.LENGTH_SHORT).show()
                isPreviewRunning = false
            } catch (e: Exception) {
                Log.e(TAG, "摄像头预览异常: ${e.message}")
                Toast.makeText(this, "摄像头预览异常", Toast.LENGTH_SHORT).show()
                isPreviewRunning = false
            }
        }
    }
    
    private fun stopCameraPreview() {
        camera?.let { cam ->
            try {
                if (isPreviewRunning) {
                    cam.stopPreview()
                    isPreviewRunning = false
                    Log.d(TAG, "摄像头预览停止")
                } else {
                    Log.d(TAG, "预览已经停止")
                }
            } catch (e: Exception) {
                Log.e(TAG, "停止摄像头预览失败: ${e.message}")
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        releaseCamera()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }
    
    private fun savePhotoToFile(data: ByteArray): File? {
        return try {
            // 创建文件名
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "INMO_AIR3_${timeStamp}.jpg"
            
            // 获取Pictures目录
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val inmoDir = File(picturesDir, "INMO_Camera")
            
            // 创建目录（如果不存在）
            if (!inmoDir.exists()) {
                inmoDir.mkdirs()
            }
            
            // 创建文件
            val photoFile = File(inmoDir, fileName)
            
            // 写入数据
            FileOutputStream(photoFile).use { fos ->
                fos.write(data)
                fos.flush()
            }
            
            Log.d(TAG, "照片已保存到: ${photoFile.absolutePath}")
            photoFile
            
        } catch (e: Exception) {
            Log.e(TAG, "保存照片失败: ${e.message}")
            null
        }
    }
    
    private fun releaseCamera() {
        camera?.let { cam ->
            try {
                isPictureTaking = false
                if (isPreviewRunning) {
                    cam.stopPreview()
                    isPreviewRunning = false
                }
                cam.release()
                camera = null
                Log.d(TAG, "摄像头资源释放")
            } catch (e: Exception) {
                Log.e(TAG, "释放摄像头资源失败: ${e.message}")
            }
        }
    }
}