package com.example.sensorscamerademo.camera

import android.Manifest
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sensorscamerademo.ui.DemoScaffold
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Barcode / QR scanner using CameraX's ImageAnalysis use case + ML Kit's
 * on-device barcode detector. The interesting parts:
 *
 *  - ImageAnalysis delivers each preview frame to our code as an
 *    ImageProxy. We have to close() it when we're done, or the camera
 *    pipeline stalls.
 *
 *  - STRATEGY_KEEP_ONLY_LATEST drops queued frames if our analyzer is
 *    slower than the camera. That keeps results fresh and avoids unbounded
 *    latency, at the cost of skipping some frames. The alternative
 *    (STRATEGY_BLOCK_PRODUCER) would force the camera to wait for us.
 *
 *  - ML Kit detection is async (returns a Task). We wire its
 *    addOnCompleteListener to close the ImageProxy — this is the canonical
 *    pattern from the ML Kit + CameraX samples.
 */
@Composable
fun BarcodeScannerScreen(onBack: () -> Unit) {
    DemoScaffold(title = "Barcode Scanner", onBack = onBack) { modifier ->
        PermissionGate(
            permissions = listOf(Manifest.permission.CAMERA),
            rationale = "Camera access is required to scan barcodes.",
            modifier = modifier
        ) {
            BarcodeContent(modifier)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeContent(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The most recent barcode and the bounding box where it was found.
    // Bounding box is null when we don't have a detection.
    var detection by remember { mutableStateOf<BarcodeResult?>(null) }
    // The size of the analyzed image, in image-coordinate space. We need
    // it to scale the bounding box into screen coordinates.
    var imageSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // ML Kit scanner. The default options scan every supported format,
    // which is what we want for a demo. A real app would limit to e.g.
    // FORMAT_QR_CODE for performance.
    val scanner = remember { BarcodeScanning.getClient() }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analyzerExecutor) { imageProxy: ImageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }
            imageSize = mediaImage.width to mediaImage.height
            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            scanner.process(input)
                .addOnSuccessListener { barcodes ->
                    val first = barcodes.firstOrNull()
                    detection = first?.let {
                        BarcodeResult(
                            value = it.rawValue ?: "",
                            format = formatName(it.format),
                            box = it.boundingBox,
                            rotationDegrees = rotation
                        )
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        // Overlay rectangle around the detected barcode.
        detection?.let { result ->
            BoxOverlay(
                box = result.box,
                rotation = result.rotationDegrees,
                imageSize = imageSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            DetectionCard(detection)
        }
    }
}

@Composable
private fun DetectionCard(detection: BarcodeResult?) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (detection == null) {
                Text(
                    "Point the camera at a barcode or QR code…",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(detection.format, style = MaterialTheme.typography.labelLarge)
                Text(
                    detection.value,
                    style = MaterialTheme.typography.titleMedium
                )
                if (isLikelyUrl(detection.value)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detection.value))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Open") }
                }
            }
            Text(
                "ImageAnalysis hands each preview frame to user code as an ImageProxy. " +
                    "We pass it to ML Kit, which runs on-device — no network required.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun BoxOverlay(
    box: Rect?,
    rotation: Int,
    imageSize: Pair<Int, Int>?,
    modifier: Modifier
) {
    if (box == null || imageSize == null) return
    val (rawW, rawH) = imageSize
    // When the image is rotated 90/270°, the width/height we should map
    // against are swapped — the camera sensor is sideways relative to the
    // portrait preview.
    val (imgW, imgH) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH

    Canvas(modifier = modifier) {
        // Center-crop: PreviewView's FILL_CENTER scales the image so it
        // covers the view, with overflow cropped. We use the larger of
        // the two scale factors and re-center.
        val scale = maxOf(size.width / imgW, size.height / imgH)
        val drawW = imgW * scale
        val drawH = imgH * scale
        val offsetX = (size.width - drawW) / 2f
        val offsetY = (size.height - drawH) / 2f

        // Rotate the box's image-space coords into the previewed coord
        // system before scaling. We treat rawW/rawH as the sensor frame.
        val rotated = rotateRect(box, rawW, rawH, rotation)

        val left = offsetX + rotated.left * scale
        val top = offsetY + rotated.top * scale
        val w = (rotated.right - rotated.left) * scale
        val h = (rotated.bottom - rotated.top) * scale

        drawRect(
            color = Color(0xFF00C853),
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 6f)
        )
    }
}

private fun rotateRect(box: Rect, srcW: Int, srcH: Int, rotation: Int): Rect = when (rotation) {
    90 -> Rect(srcH - box.bottom, box.left, srcH - box.top, box.right)
    180 -> Rect(srcW - box.right, srcH - box.bottom, srcW - box.left, srcH - box.top)
    270 -> Rect(box.top, srcW - box.right, box.bottom, srcW - box.left)
    else -> box
}

private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    else -> "UNKNOWN ($format)"
}

private fun isLikelyUrl(value: String): Boolean =
    value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)

private data class BarcodeResult(
    val value: String,
    val format: String,
    val box: Rect?,
    val rotationDegrees: Int
)

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resumeWith(Result.success(get()))
            } catch (t: Throwable) {
                cont.resumeWith(Result.failure(t))
            }
        }, Runnable::run)
    }
