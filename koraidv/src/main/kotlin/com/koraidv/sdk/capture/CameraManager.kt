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
        android.util.Log.d("KoraIDV", "CameraManager.initialize() position=$position lifecycle=${lifecycleOwner.lifecycle.currentState}")
        currentPosition = position
        boundLifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(this)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                android.util.Log.d("KoraIDV", "CameraManager: provider obtained, binding use cases")
                bindCameraUseCases(lifecycleOwner, previewView)
                android.util.Log.d("KoraIDV", "CameraManager: bind succeeded, camera=$camera")
                onInitialized(true)
            } catch (e: Exception) {
                android.util.Log.e("KoraIDV", "CameraManager: initialize failed", e)
                onInitialized(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return

        // Unbind ALL use cases before binding new ones. The ProcessCameraProvider
        // is a singleton — another CameraManager (e.g. selfie screen) may still have
        // use cases bound when this one initializes (race between Compose
        // DisposableEffect.onDispose and AndroidView.factory). unbindAll() is safe
        // because only one camera screen is active at a time in the verification flow.
        cameraProvider.unbindAll()
        preview = null
        imageCapture = null
        imageAnalyzer = null
        camera = null

        // FR-003.4 · Share 4:3 across all three use cases so the ViewPort
        // binding below reconciles into one consistent crop. Previously
        // Preview + Capture were 16:9 while Analysis was 4:3, which made
        // CameraX pick a smaller common area and cropped more aggressively
        // than the user could see in the viewfinder. 4:3 also gives better
        // pixel density for a horizontal 1.586:1 card than 16:9.
        val sharedResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        // Preview
        preview = Preview.Builder()
            .setResolutionSelector(sharedResolution)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image capture — prioritise quality over latency. ID verification
        // cares far more about sharp text / readable MRZ than about
        // shutter responsiveness. MINIMIZE_LATENCY was the previous setting
        // and produced soft captures under moderate lighting.
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(sharedResolution)
            .build()

        // Image analysis
        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(sharedResolution)
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
            // REQ-003 FR-003.4 · Share a common crop rect between the
            // preview, analysis, and capture use cases via a ViewPort.
            //
            // We construct one from the viewfinder's aspect ratio (1.586:1
            // for ID-1 cards) rather than reading `previewView.viewPort`,
            // because that property is null until after the first layout
            // pass — and the Compose AndroidView factory calls initialize()
            // BEFORE layout, so the property-based path was silently
            // falling through to the old independent-binding behaviour
            // (full 4:3 sensor capture with desk visible beyond the
            // viewfinder edges).
            //
            // Hard-coding the ratio here is correct because the viewfinder
            // on the capture screen is always 1.586:1 (ID-1 aspect).
            val displayRotation = previewView.display?.rotation
                ?: android.view.Surface.ROTATION_0
            val viewPort = ViewPort.Builder(
                android.util.Rational(1586, 1000),
                displayRotation,
            ).setScaleType(ViewPort.FILL_CENTER).build()
            val group = UseCaseGroup.Builder()
                .addUseCase(preview!!)
                .addUseCase(imageCapture!!)
                .addUseCase(imageAnalyzer!!)
                .setViewPort(viewPort)
                .build()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, group)

            // Enable continuous auto-focus on center of frame
            camera?.cameraControl?.let { control ->
                try {
                    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                    val centerPoint = factory.createPoint(0.5f, 0.5f)
                    val action = FocusMeteringAction.Builder(
                        centerPoint,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    )
                        .setAutoCancelDuration(5, TimeUnit.SECONDS)
                        .build()
                    control.startFocusAndMetering(action)
                } catch (_: Exception) {
                    // Auto-focus setup is best-effort; camera still works without it
                }
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
        android.util.Log.d("KoraIDV", "CameraManager.release() cameraProvider=${cameraProvider != null}")
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
