/*
 * ============================================================================
 * VideoRecordingScreen — CameraX video capture demo
 * ============================================================================
 *
 *  ---  What a basic CameraX *photo* app would look like  --------------------
 *
 *  Although this screen records video, the simplest CameraX use case is
 *  taking a still photo. Conceptually it is almost identical to what you see
 *  below; the only differences are:
 *
 *   1.  Swap the `VideoCapture<Recorder>` use case for an `ImageCapture`.
 *
 *          val imageCapture = ImageCapture.Builder()
 *              .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
 *              .build()
 *
 *   2.  Bind `Preview` + `ImageCapture` to the lifecycle (instead of
 *       Preview + VideoCapture):
 *
 *          cameraProvider.bindToLifecycle(
 *              lifecycleOwner, cameraSelector, preview, imageCapture
 *          )
 *
 *   3.  On the shutter-button tap, call `takePicture(...)` and write the
 *       resulting bytes to MediaStore (Pictures/SensorsCameraDemo/).
 *       There is no Recording object to manage and no audio permission.
 *
 *  Everything else — the `PreviewView`, the lifecycle-aware binding, the
 *  `MediaStore` output, the permission request — is the same. That's the
 *  point of CameraX: switching from photo to video is a matter of changing
 *  one use case, not rewriting the camera pipeline.
 *
 *  This corresponds to "Camera demo #1" from the original presentation
 *  outline, which we cover verbally rather than as a separate screen.
 *
 *  ---  How CameraX is structured  ------------------------------------------
 *
 *  ProcessCameraProvider is a singleton that owns the camera device.
 *  bindToLifecycle() ties the camera to a LifecycleOwner (the Activity in
 *  our case) — when that owner is RESUMED the camera opens; when it stops
 *  the camera is released automatically. We do not call open()/close()
 *  ourselves, which is the single biggest ergonomic win over Camera2.
 *
 *  Use cases CameraX exposes:
 *     - Preview        — feeds frames into a Surface (PreviewView)
 *     - ImageCapture   — high-quality still photos
 *     - VideoCapture   — encoded video (used here, with a Recorder)
 *     - ImageAnalysis  — gives each frame to your code (see BarcodeScanner)
 *
 *  A camera can usually bind 2 of these at once; the documented "guaranteed"
 *  combinations are listed in the CameraX docs. We bind Preview + VideoCapture.
 *
 *  ---  Where the file ends up  ---------------------------------------------
 *
 *  Before Android 10 you needed WRITE_EXTERNAL_STORAGE plus a hard path on
 *  the SD card. Since Android 10, MediaStore handles that for us under the
 *  Scoped Storage model — we hand MediaStore a relative path
 *  ("Movies/SensorsCameraDemo/") and it puts the file in the right place,
 *  visible to the gallery, without any storage permission.
 *
 *  ---  Front vs back camera  -----------------------------------------------
 *
 *  Switching cameras means re-binding the same use cases against a different
 *  CameraSelector. We can't just "set" the camera on an existing binding;
 *  bindToLifecycle returns a new Camera handle each time.
 *
 * ============================================================================
 */
package com.example.sensorscamerademo.camera

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sensorscamerademo.ui.DemoScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun VideoRecordingScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }

    DemoScaffold(
        title = "Video Recording (CameraX)",
        onBack = onBack,
        snackbarHostState = snackbar
    ) { modifier ->
        PermissionGate(
            permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            rationale = "We need camera and microphone access to record video.",
            modifier = modifier
        ) {
            VideoContent(modifier = modifier, snackbar = snackbar)
        }
    }
}

@Composable
private fun VideoContent(modifier: Modifier, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Which way the camera is pointing. Flipping this triggers re-binding
    // in the LaunchedEffect below.
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    // PreviewView is a regular Android View — we host it in Compose with
    // AndroidView. CameraX writes frames into the SurfaceProvider it exposes.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // The current VideoCapture use case. We store it so the record button
    // can start a Recording against it.
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    // The active Recording, if one is in progress. Null when idle.
    var recording by remember { mutableStateOf<Recording?>(null) }

    // Wall-clock start time so we can show "0:08" while recording.
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf("0") }

    // Bind the camera. Runs once on first composition, and again every time
    // `cameraSelector` changes (front/back flip). We need to unbind before
    // re-binding because CameraX won't let the same use case be bound twice.
    LaunchedEffect(cameraSelector) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        // Recorder is the VideoCapture backend. .build() returns a default
        // quality selector that picks the highest available quality the
        // device supports.
        val recorder = Recorder.Builder().build()
        val capture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            videoCapture = capture
        } catch (t: Throwable) {
            // Some devices (e.g. emulators) lack the requested camera entirely.
            videoCapture = null
            snackbar.showSnackbar("Could not start camera: ${t.message}")
        }
    }

    // Tick once per second while a recording is active so the elapsed-time
    // text stays current. Stops when `recording` becomes null again.
    LaunchedEffect(recording) {
        while (recording != null) {
            val seconds = (System.currentTimeMillis() - recordingStartMs) / 1000
            elapsedSeconds = seconds.toString()
            delay(1000)
        }
        elapsedSeconds = "0"
    }

    // When the screen leaves composition, make sure any in-flight recording
    // is stopped. CameraX will also release the camera as the lifecycle
    // owner moves to STOPPED, but the Recording object is ours to manage.
    DisposableEffect(Unit) {
        onDispose { recording?.stop() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        // Top-right camera-flip button.
        IconButton(
            onClick = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                Icons.Filled.Cameraswitch,
                contentDescription = "Flip camera",
                tint = Color.White
            )
        }

        // Recording elapsed timer (only shown while a recording is active).
        if (recording != null) {
            Text(
                text = "● ${elapsedSeconds}s",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // The big circular record/stop button.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    val capture = videoCapture ?: return@FloatingActionButton
                    val active = recording
                    if (active != null) {
                        active.stop()
                        recording = null
                    } else {
                        recording = startRecording(
                            context = context,
                            videoCapture = capture,
                            onSaved = {
                                scope.launch { snackbar.showSnackbar("Video saved to gallery.") }
                                recording = null
                            }
                        )
                        recordingStartMs = System.currentTimeMillis()
                    }
                },
                containerColor = if (recording != null) Color(0xFFC62828)
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (recording != null) Icons.Filled.Stop
                    else Icons.Filled.FiberManualRecord,
                    contentDescription = if (recording != null) "Stop" else "Record",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Build a MediaStore-backed output and start a recording. The returned
 * [Recording] is what we stop later. We enable audio because we requested
 * RECORD_AUDIO above.
 */
private fun startRecording(
    context: android.content.Context,
    videoCapture: VideoCapture<Recorder>,
    onSaved: () -> Unit
): Recording {
    val name = "demo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        // RELATIVE_PATH is the MediaStore way of placing files in a sub-
        // folder under Movies/ — available on Q+ (API 29). For lower APIs
        // the file just goes to the Movies root, which is fine for a demo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SensorsCameraDemo")
        }
    }
    val output = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    val recordingBuilder = videoCapture.output
        .prepareRecording(context, output)
        .withAudioEnabled()
    return recordingBuilder.start(ContextCompat.getMainExecutor(context)) { event ->
        if (event is VideoRecordEvent.Finalize) {
            onSaved()
        }
    }
}

/**
 * Tiny await helper — ProcessCameraProvider.getInstance returns a
 * ListenableFuture, which is awkward to use from Compose directly.
 */
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
