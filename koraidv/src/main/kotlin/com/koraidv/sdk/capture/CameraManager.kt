package com.koraidv.sdk.capture

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera position enum
 */
enum class CameraPosition {
    FRONT,
    BACK
}

/**
 * Camera manager for handling camera operations using CameraX
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var currentPosition = CameraPosition.BACK
    private var onFrameListener: ((ImageProxy) -> Unit)? = null

    /**
     * Initialize camera with the given preview view
     */
    fun initialize(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        position: CameraPosition = CameraPosition.BACK,
        onInitialized: (Boolean) -> Unit
    ) {
        currentPosition = position

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView)
                onInitialized(true)
            } catch (e: Exception) {
                onInitialized(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return

        // Preview
        preview = Preview.Builder()
            .setTargetResolution(Size(1280, 720))
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(Size(1920, 1080))
            .build()

        // Image analysis
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    onFrameListener?.invoke(imageProxy)
                    imageProxy.close()
                }
            }

        // Select camera
        val cameraSelector = when (currentPosition) {
            CameraPosition.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraPosition.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Set frame analysis listener
     */
    fun setFrameAnalysisListener(listener: (ImageProxy) -> Unit) {
        onFrameListener = listener
    }

    /**
     * Capture a photo
     */
    fun capturePhoto(onCaptured: (ByteArray?) -> Unit) {
        val imageCapture = imageCapture ?: run {
            onCaptured(null)
            return
        }

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    onCaptured(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    onCaptured(null)
                }
            }
        )
    }

    /**
     * Switch camera position
     */
    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        currentPosition = if (currentPosition == CameraPosition.BACK) {
            CameraPosition.FRONT
        } else {
            CameraPosition.BACK
        }
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    /**
     * Set zoom level (0.0 to 1.0)
     */
    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    /**
     * Set focus point
     */
    fun focus(x: Float, y: Float) {
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    /**
     * Toggle flash
     */
    fun toggleFlash(): Boolean {
        val currentFlashMode = imageCapture?.flashMode ?: ImageCapture.FLASH_MODE_OFF
        val newFlashMode = if (currentFlashMode == ImageCapture.FLASH_MODE_OFF) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = newFlashMode
        return newFlashMode == ImageCapture.FLASH_MODE_ON
    }

    /**
     * Release camera resources
     */
    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
