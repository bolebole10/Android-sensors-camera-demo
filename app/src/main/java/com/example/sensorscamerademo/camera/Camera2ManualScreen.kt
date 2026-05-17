package com.example.sensorscamerademo.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sensorscamerademo.ui.DemoScaffold
import com.example.sensorscamerademo.ui.ExplanationCard
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun Camera2ManualScreen(onBack: () -> Unit) {
    DemoScaffold(title = "Manual Controls (Camera2)", onBack = onBack) { modifier ->
        PermissionGate(
            permissions = listOf(Manifest.permission.CAMERA),
            rationale = "Camera access is required for manual exposure control.",
            modifier = modifier
        ) {
            Camera2Content(modifier)
        }
    }
}

@Composable
private fun Camera2Content(modifier: Modifier) {
    val context = LocalContext.current

    // Camera2 operacije moraju biti na pozadinskoj niti
    val cameraThread = remember {
        HandlerThread("Camera2Thread").apply { start() }
    }
    val cameraHandler = remember { Handler(cameraThread.looper) }

    // Dohvati stražnju kameru i njene mogućnosti (ISO raspon, ekspozicija, fokus)
    val cameraInfo = remember {
        try {
            findBackCamera(context)
        } catch (t: Throwable) {
            null
        }
    }

    if (cameraInfo == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No usable back camera was found on this device.")
        }
        return
    }

    val supportsManual = cameraInfo.supportsManualSensor

    var autoMode by remember { mutableStateOf(true) }
    // Početne vrijednosti slidera — sredina raspona da slika ne bude crna
    var iso by remember {
        val mid = (cameraInfo.isoRange.lower + cameraInfo.isoRange.upper) / 2
        mutableFloatStateOf(mid.coerceAtLeast(400).toFloat())
    }
    var exposureNs by remember {
        val target = 1_000_000_000L / 60  // ~1/60s
        mutableLongStateOf(
            target.coerceIn(cameraInfo.exposureRangeNs.lower, cameraInfo.exposureRangeNs.upper)
        )
    }
    var focusDist by remember { mutableFloatStateOf(0f) }  // 0 = beskonačnost
    var lastSavedUri by remember { mutableStateOf<String?>(null) }

    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    var imageReader by remember { mutableStateOf<ImageReader?>(null) }
    // TextureView — ručni preview (CameraX koristi PreviewView umjesto ovoga)
    val textureView = remember { TextureView(context) }

    // Svaki put kad korisnik pomakne slider → novi CaptureRequest
    LaunchedEffect(autoMode, iso, exposureNs, focusDist, captureSession) {
        val session = captureSession ?: return@LaunchedEffect
        val device = cameraDevice ?: return@LaunchedEffect
        val surface = previewSurface ?: return@LaunchedEffect
        val request = buildPreviewRequest(
            device = device,
            surface = surface,
            autoMode = autoMode,
            iso = iso.toInt(),
            exposureNs = exposureNs,
            focusDist = focusDist
        )
        try {
            // Repeating request — kontinuirani preview (bez ovoga = crni ekran)
            session.setRepeatingRequest(request, null, cameraHandler)
        } catch (_: Throwable) {}
    }

    // Otvaranje kamere — lanac callbackova koji CameraX radi umjesto nas
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val reader = ImageReader.newInstance(
            cameraInfo.captureSize.width,
            cameraInfo.captureSize.height,
            android.graphics.ImageFormat.JPEG,
            2
        )
        imageReader = reader

        fun openCameraOnceSurfaceReady(surfaceTex: SurfaceTexture) {
            surfaceTex.setDefaultBufferSize(1280, 720)
            val surface = Surface(surfaceTex)
            previewSurface = surface

            @Suppress("MissingPermission")
            manager.openCamera(cameraInfo.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    // Sessija s dva outputa: preview surface + ImageReader (za foto)
                    val outputs = listOf(surface, reader.surface)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val executor = java.util.concurrent.Executor { cameraHandler.post(it) }
                        device.createCaptureSession(
                            android.hardware.camera2.params.SessionConfiguration(
                                android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                                outputs.map { android.hardware.camera2.params.OutputConfiguration(it) },
                                executor,
                                sessionStateCallback { captureSession = it }
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        device.createCaptureSession(
                            outputs,
                            sessionStateCallback { captureSession = it },
                            cameraHandler
                        )
                    }
                }
                override fun onDisconnected(device: CameraDevice) { device.close() }
                override fun onError(device: CameraDevice, error: Int) { device.close() }
            }, cameraHandler)
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
                openCameraOnceSurfaceReady(t)
            }
            override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(t: SurfaceTexture) = Unit
        }
        textureView.surfaceTexture?.let(::openCameraOnceSurfaceReady)

        // POKAZATI
        onDispose {
            // Zatvaranje u obrnutom redoslijedu — CameraX bi ovo napravio za nas
            try { captureSession?.close() } catch (_: Throwable) {}
            captureSession = null
            try { cameraDevice?.close() } catch (_: Throwable) {}
            cameraDevice = null
            previewSurface?.release()
            previewSurface = null
            try { imageReader?.close() } catch (_: Throwable) {}
            imageReader = null
            cameraThread.quitSafely()
        }
    }

    var controlsExpanded by remember { mutableStateOf(true) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Full-screen camera preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { textureView }
        )

        // Translucent controls overlay at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .animateContentSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 30) controlsExpanded = false
                        if (dragAmount < -30) controlsExpanded = true
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Drag handle + toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { controlsExpanded = !controlsExpanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Visual drag indicator
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Collapsed: show minimal info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (autoMode) "Auto" else "Manual",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = if (controlsExpanded) Icons.Default.KeyboardArrowDown
                    else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (controlsExpanded) "Collapse" else "Expand",
                    tint = Color.White
                )
            }

            if (controlsExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                // Auto / manual toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (autoMode) "Auto mode" else "Manual mode",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = !autoMode,
                        onCheckedChange = { autoMode = !it },
                        enabled = supportsManual
                    )
                    if (!supportsManual) {
                        Text(
                            "No manual support",
                            fontSize = 11.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }

                val slidersEnabled = !autoMode && supportsManual

                Spacer(modifier = Modifier.height(4.dp))

                OverlaySlider(
                    label = "ISO",
                    value = iso,
                    valueRange = cameraInfo.isoRange.lower.toFloat()..cameraInfo.isoRange.upper.toFloat(),
                    display = iso.toInt().toString(),
                    enabled = slidersEnabled,
                    onValueChange = { iso = it }
                )
                OverlaySlider(
                    label = "Ekspozicija",
                    value = exposureNs.toFloat(),
                    valueRange = cameraInfo.exposureRangeNs.lower.toFloat()..cameraInfo.exposureRangeNs.upper.toFloat(),
                    display = "%.2f ms".format(exposureNs / 1_000_000.0),
                    enabled = slidersEnabled,
                    onValueChange = { exposureNs = it.toLong() }
                )
                OverlaySlider(
                    label = "Fokus",
                    value = focusDist,
                    valueRange = 0f..cameraInfo.minFocusDistance,
                    display = if (focusDist == 0f) "∞" else "%.2f diopt.".format(focusDist),
                    enabled = slidersEnabled,
                    onValueChange = { focusDist = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    onClick = {
                        val session = captureSession ?: return@Button
                        val device = cameraDevice ?: return@Button
                        val reader = imageReader ?: return@Button
                        takePhoto(
                            context = context,
                            device = device,
                            session = session,
                            reader = reader,
                            handler = cameraHandler,
                            autoMode = autoMode,
                            iso = iso.toInt(),
                            exposureNs = exposureNs,
                            focusDist = focusDist,
                            onSaved = { uri -> lastSavedUri = uri }
                        )
                    }
                ) {
                    Text("Capture")
                }

                lastSavedUri?.let {
                    Text(
                        "Saved: $it",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlaySlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            valueRange = valueRange,
            enabled = enabled,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.8f),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.Gray.copy(alpha = 0.5f),
                disabledInactiveTrackColor = Color.Gray.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                display,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ---- Camera2 plumbing -----------------------------------------------------

private data class BackCamera(
    val cameraId: String,
    val supportsManualSensor: Boolean,
    val isoRange: Range<Int>,
    val exposureRangeNs: Range<Long>,
    val minFocusDistance: Float,
    val captureSize: Size
)

// POKAZATI
private fun findBackCamera(context: Context): BackCamera? {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    for (id in manager.cameraIdList) {
        val ch = manager.getCameraCharacteristics(id)
        val facing = ch.get(CameraCharacteristics.LENS_FACING) ?: continue
        if (facing != CameraCharacteristics.LENS_FACING_BACK) continue

        // Provjera podržava li uređaj manual mode
        val capabilities = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val supportsManual = capabilities.any {
            it == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        }

        // Dohvat raspona parametara iz CameraCharacteristics
        val isoRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ?: Range(100, 800)
        val rawExpRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?: Range(1_000_000L, 100_000_000L)
        // Ograničavamo na max 125 ms da preview ne izgleda kao da je zamrznut
        val expRange = Range(
            rawExpRange.lower,
            minOf(rawExpRange.upper, 125_000_000L).coerceAtLeast(rawExpRange.lower)
        )
        val minFocus = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        val configMap = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSize = configMap
            ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            ?.minByOrNull { kotlin.math.abs(it.width * it.height - 1280 * 720) }
            ?: Size(1280, 720)

        return BackCamera(
            cameraId = id,
            supportsManualSensor = supportsManual,
            isoRange = isoRange,
            exposureRangeNs = expRange,
            minFocusDistance = minFocus,
            captureSize = jpegSize
        )
    }
    return null
}

private fun buildPreviewRequest(
    device: CameraDevice,
    surface: Surface,
    autoMode: Boolean,
    iso: Int,
    exposureNs: Long,
    focusDist: Float
): CaptureRequest {
    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    builder.addTarget(surface)
    applyControls(builder, autoMode, iso, exposureNs, focusDist)
    return builder.build()
}

// POKAZATI
// Postavljanje auto/manual kontrola na CaptureRequest
private fun applyControls(
    builder: CaptureRequest.Builder,
    autoMode: Boolean,
    iso: Int,
    exposureNs: Long,
    focusDist: Float
) {
    if (autoMode) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO) //ISO
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON) //EKSPOZICIJA
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) //FOKUS
    } else {
        // Isključi sve auto kontrole
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
        // Ručne vrijednosti: ISO, ekspozicija (ns), fokus (diopt., 0=∞)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDist)
    }
}

private fun sessionStateCallback(
    onConfigured: (CameraCaptureSession) -> Unit
) = object : CameraCaptureSession.StateCallback() {
    override fun onConfigured(session: CameraCaptureSession) { onConfigured(session) }
    override fun onConfigureFailed(session: CameraCaptureSession) {}
}

private fun takePhoto(
    context: Context,
    device: CameraDevice,
    session: CameraCaptureSession,
    reader: ImageReader,
    handler: Handler,
    autoMode: Boolean,
    iso: Int,
    exposureNs: Long,
    focusDist: Float,
    onSaved: (String) -> Unit
) {
    // Kad ImageReader primi JPEG → spremi u MediaStore
    reader.setOnImageAvailableListener({ r ->
        val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
        try {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val uri = writeJpegToMediaStore(context, bytes)
            handler.post { onSaved(uri ?: "(failed)") }
        } finally {
            image.close()
        }
    }, handler)

    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
    builder.addTarget(reader.surface)
    applyControls(builder, autoMode, iso, exposureNs, focusDist)
    builder.set(CaptureRequest.JPEG_ORIENTATION, 90)

    session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            @Suppress("UNUSED_VARIABLE")
            val achievedIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        }
    }, handler)
}

private fun writeJpegToMediaStore(context: Context, jpegBytes: ByteArray): String? {
    val name = "photo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SensorsCameraDemo")
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    val stream: OutputStream = resolver.openOutputStream(uri) ?: return null
    stream.use { it.write(jpegBytes) }
    return uri.toString()
}
