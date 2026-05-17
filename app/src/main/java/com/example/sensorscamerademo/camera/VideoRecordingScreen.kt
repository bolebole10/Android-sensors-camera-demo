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

    // Odabir kamere — promjena okida rebind u LaunchedEffectu
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    // PreviewView — CameraX View koji prikazuje camera feed
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    // Aktivno snimanje — null kad ne snimamo
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf("0") }

    // Bind kamere — pokrene se na početku i svaki put kad se promijeni front/back
    LaunchedEffect(cameraSelector) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val recorder = Recorder.Builder().build()
        val capture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            // bindToLifecycle — 1 poziv veže kameru za lifecycle Activitya
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            videoCapture = capture
        } catch (t: Throwable) {
            videoCapture = null
            snackbar.showSnackbar("Could not start camera: ${t.message}")
        }
    }

    LaunchedEffect(recording) {
        while (recording != null) {
            val seconds = (System.currentTimeMillis() - recordingStartMs) / 1000
            elapsedSeconds = seconds.toString()
            delay(1000)
        }
        elapsedSeconds = "0"
    }

    // Zaustavi snimanje kad korisnik napusti ekran
    DisposableEffect(Unit) {
        onDispose { recording?.stop() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

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

// Pokreni snimanje — sprema u MediaStore (Movies/SensorsCameraDemo/)
private fun startRecording(
    context: android.content.Context,
    videoCapture: VideoCapture<Recorder>,
    onSaved: () -> Unit
): Recording {
    val name = "demo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        // Scoped Storage (API 29+) — ne treba WRITE_EXTERNAL_STORAGE permissija
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
