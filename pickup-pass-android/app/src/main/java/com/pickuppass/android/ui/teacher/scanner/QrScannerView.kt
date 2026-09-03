package com.pickuppass.android.ui.teacher.scanner

import android.util.Size
import androidx.camera.core.CameraSelector
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

/**
 * Live CameraX + ML Kit QR scanner.
 *
 * This composable owns its CameraX use cases. When it leaves composition the
 * preview and analysis use cases are explicitly unbound, so the camera is not
 * left running while staff review guardian/student details or an error state.
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
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context)
                ) { imageProxy ->
                    if (currentPaused.value || session.disposed) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
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
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { currentOnQrDetected.value(it) }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                session.provider = cameraProvider
                session.preview = preview
                session.analysis = analysis

                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        }
    )

    DisposableEffect(scanner) {
        session.disposed = false
        onDispose {
            session.disposed = true
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
            session.clear()
        }
    }
}

private class ScannerCameraSession {
    var provider: ProcessCameraProvider? = null
    var preview: Preview? = null
    var analysis: ImageAnalysis? = null
    var disposed: Boolean = false

    fun clear() {
        provider = null
        preview = null
        analysis = null
    }
}
