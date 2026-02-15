package com.example.telegramlistener.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class CameraHelper(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    fun takePhoto(cameraId: String, outputFile: File, onComplete: (Boolean) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onComplete(false)
            return
        }

        startBackgroundThread()

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Find a valid camera if default "0" is not available or valid
            val id = if (manager.cameraIdList.contains(cameraId)) cameraId else manager.cameraIdList.firstOrNull()
            
            if (id == null) {
                onComplete(false)
                return
            }

            // Prepare ImageReader
            val characteristics = manager.getCameraCharacteristics(id)
            val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = configMap?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height } 
                ?: android.util.Size(640, 480)
            
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                val buffer: ByteBuffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                FileOutputStream(outputFile).use { it.write(bytes) }
                image.close()
                closeCamera()
                stopBackgroundThread()
                onComplete(true)
            }, backgroundHandler)

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    closeCamera()
                    onComplete(false)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    closeCamera()
                    onComplete(false)
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e("CameraHelper", "Camera error", e)
            closeCamera()
            stopBackgroundThread()
            onComplete(false)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val surface = imageReader?.surface ?: return
            
            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    try {
                        val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureBuilder?.addTarget(surface)
                        // Auto focus and flash if available
                        captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        captureBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        
                        session.capture(captureBuilder?.build()!!, null, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e("CameraHelper", "Capture error", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraHelper", "Configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraHelper", "Session error", e)
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("CameraHelper", "Thread stop error", e)
        }
    }

    private fun closeCamera() {
        try {
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e("CameraHelper", "Close error", e)
        }
    }
}
