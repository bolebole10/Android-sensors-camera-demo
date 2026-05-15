package com.example.sensorscamerademo.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sensorscamerademo.sensors.DemoCard
import com.example.sensorscamerademo.sensors.DemoEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraMenuScreen(
    onOpenVideo: () -> Unit,
    onOpenBarcode: () -> Unit,
    onOpenCamera2: () -> Unit
) {
    val items = listOf(
        DemoEntry(
            title = "Video Recording (CameraX)",
            subtitle = "Preview + VideoCapture, save to gallery",
            icon = Icons.Filled.Videocam,
            onClick = onOpenVideo
        ),
        DemoEntry(
            title = "Barcode & QR Scanner",
            subtitle = "CameraX ImageAnalysis + ML Kit",
            icon = Icons.Filled.QrCodeScanner,
            onClick = onOpenBarcode
        ),
        DemoEntry(
            title = "Manual Controls (Camera2)",
            subtitle = "ISO, exposure, focus — the verbose API",
            icon = Icons.Filled.Settings,
            onClick = onOpenCamera2
        )
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Camera") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { entry -> DemoCard(entry) }
        }
    }
}
