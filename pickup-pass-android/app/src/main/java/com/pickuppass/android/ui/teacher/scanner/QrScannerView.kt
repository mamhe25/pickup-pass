package com.pickuppass.android.ui.teacher.scanner

import android.util.Size
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live CameraX + ML Kit QR scanner optimized for gate throughput.
 *
 * Analysis runs on a dedicated background thread, only QR codes are decoded,
 * stale frames are discarded, and tap-to-focus is available for difficult
 * screens/lighting. The lower analysis target keeps enough QR detail while
 * reducing the amount of image data ML Kit must inspect per frame.
 */
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    paused: Boolean,
    onQrDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPaused = rememberUpdatedState(paused)
    val currentOnQrDetected = rememberUpdatedState(onQrDetected)
    val session = remember { ScannerCameraSession() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = runCatching { cameraProviderFuture.get() }
                    .getOrNull()
                    ?: return@addListener

                if (session.disposed) return@addListener

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(960, 540),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (currentPaused.value || session.disposed) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    if (!session.processing.compareAndSet(false, true)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        session.processing.set(false)
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (!session.disposed && !currentPaused.value) {
                                barcodes.firstOrNull()
                                    ?.rawValue
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { currentOnQrDetected.value(it) }
                            }
                        }
                        .addOnCompleteListener {
                            session.processing.set(false)
                            imageProxy.close()
                        }
                }

                session.provider = cameraProvider
                session.preview = preview
                session.analysis = analysis

                runCatching {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    session.camera = camera
                    configureFastFocus(previewView, camera)
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        }
    )

    DisposableEffect(scanner, analysisExecutor) {
        session.disposed = false
        onDispose {
            session.disposed = true
            session.processing.set(false)
            session.analysis?.clearAnalyzer()
            session.provider?.let { provider ->
                session.preview?.let { preview ->
                    runCatching { provider.unbind(preview) }
                }
                session.analysis?.let { analysis ->
                    runCatching { provider.unbind(analysis) }
                }
            }
            scanner.close()
            analysisExecutor.shutdownNow()
            session.clear()
        }
    }
}

private fun configureFastFocus(
    previewView: PreviewView,
    camera: Camera
) {
    fun focusAt(x: Float, y: Float) {
        if (previewView.width <= 0 || previewView.height <= 0) return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        runCatching { camera.cameraControl.startFocusAndMetering(action) }
    }

    // Prime focus/exposure in the center as soon as the preview is laid out.
    previewView.post {
        focusAt(
            previewView.width / 2f,
            previewView.height / 2f
        )
    }

    previewView.setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                focusAt(event.x, event.y)
                view.performClick()
                true
            }
            MotionEvent.ACTION_DOWN -> true
            else -> false
        }
    }
}

private class ScannerCameraSession {
    var provider: ProcessCameraProvider? = null
    var preview: Preview? = null
    var analysis: ImageAnalysis? = null
    var camera: Camera? = null
    var disposed: Boolean = false
    val processing = AtomicBoolean(false)

    fun clear() {
        provider = null
        preview = null
        analysis = null
        camera = null
    }
}
