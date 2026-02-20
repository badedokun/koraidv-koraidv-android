package com.koraidv.sdk.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import java.io.ByteArrayOutputStream
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Camera position enum
 */
enum class CameraPosition {
    FRONT,
    BACK
}

/**
 * Camera manager for handling camera operations using CameraX.
 * Implements [DefaultLifecycleObserver] to automatically release resources when
 * the lifecycle owner is destroyed, preventing camera leaks.
 */
class CameraManager(private val context: Context) : DefaultLifecycleObserver {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPosition = CameraPosition.BACK
    private var onFrameListener: ((ImageProxy) -> Unit)? = null
    private var boundLifecycleOwner: LifecycleOwner? = null

    /**
     * Initialize camera with the given preview view.
     * Automatically registers as a lifecycle observer to clean up on destroy.
     */
    fun initialize(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        position: CameraPosition = CameraPosition.BACK,
        onInitialized: (Boolean) -> Unit
    ) {
        currentPosition = position
        boundLifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(this)

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

        // Use ResolutionSelector (CameraX 1.3+ replacement for deprecated setTargetResolution)
        val previewResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val captureResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val analysisResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        // Preview
        preview = Preview.Builder()
            .setResolutionSelector(previewResolution)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(captureResolution)
            .build()

        // Image analysis
        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    val listener = onFrameListener
                    if (listener != null) {
                        // Listener takes ownership and must close the imageProxy
                        listener.invoke(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }
            }

        // Select camera
        val cameraSelector = when (currentPosition) {
            CameraPosition.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraPosition.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            // Unbind only this instance's use cases to avoid killing cameras
            // bound by other CameraManager instances sharing the singleton provider.
            listOfNotNull(preview, imageCapture, imageAnalyzer).let { existing ->
                if (existing.isNotEmpty()) cameraProvider.unbind(*existing.toTypedArray())
            }
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )

            // Enable continuous auto-focus on center of frame
            camera?.cameraControl?.let { control ->
                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                val centerPoint = factory.createPoint(0.5f, 0.5f)
                val action = FocusMeteringAction.Builder(
                    centerPoint,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                    .setAutoCancelDuration(0, TimeUnit.SECONDS) // Continuous — never cancel
                    .build()
                control.startFocusAndMetering(action)
            }
        } catch (e: Exception) {
            android.util.Log.e("KoraIDV", "Failed to bind camera use cases", e)
        }
    }

    /**
     * Set frame analysis listener
     */
    fun setFrameAnalysisListener(listener: (ImageProxy) -> Unit) {
        onFrameListener = listener
    }

    /**
     * Capture a photo. Callback is invoked on the camera executor thread.
     * Properly recycles intermediate bitmaps to prevent OOM on low-end devices.
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
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()

                    // Apply rotation to produce correctly oriented JPEG
                    if (rotation != 0) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val matrix = Matrix()
                            matrix.postRotate(rotation.toFloat())
                            val rotated = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                            // Recycle the original bitmap immediately — it's no longer needed
                            if (rotated !== bitmap) {
                                bitmap.recycle()
                            }
                            val stream = ByteArrayOutputStream()
                            rotated.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            rotated.recycle()
                            onCaptured(stream.toByteArray())
                            return
                        }
                        // BitmapFactory.decodeByteArray returned null — image data is corrupt.
                        // Fall through to return raw bytes (server will reject with a clear error).
                    }
                    onCaptured(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    onCaptured(null)
                }
            }
        )
    }

    /**
     * Capture a photo with callback on the main thread.
     * Safe for updating Compose state from the callback.
     */
    fun capturePhotoOnMain(onCaptured: (ByteArray?) -> Unit) {
        capturePhoto { bytes ->
            mainHandler.post { onCaptured(bytes) }
        }
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

    // DefaultLifecycleObserver — auto-release on destroy
    override fun onDestroy(owner: LifecycleOwner) {
        release()
        owner.lifecycle.removeObserver(this)
    }

    /**
     * Release camera resources.
     * Called automatically when the bound lifecycle owner is destroyed.
     */
    fun release() {
        // Only unbind this instance's use cases — NOT unbindAll() which would
        // kill cameras bound by other CameraManager instances sharing the
        // singleton ProcessCameraProvider.
        val provider = cameraProvider ?: return
        listOfNotNull(preview, imageCapture, imageAnalyzer).let { useCases ->
            if (useCases.isNotEmpty()) {
                provider.unbind(*useCases.toTypedArray())
            }
        }
        preview = null
        imageCapture = null
        imageAnalyzer = null
        camera = null
        cameraProvider = null
        onFrameListener = null
        boundLifecycleOwner = null
        cameraExecutor.shutdown()
    }
}
