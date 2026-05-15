package com.example.sensorscamerademo.camera

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Wraps a composable so it only runs once all the listed permissions are
 * granted. If they're not, the user sees a "Grant Permission" button and
 * a short explanation — tapping it triggers the runtime permission
 * request. As soon as they're granted, [content] is composed.
 *
 * We do this with Compose's [rememberLauncherForActivityResult] rather
 * than pulling in `accompanist-permissions`, so there's one fewer
 * dependency to explain in the presentation.
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationale: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    fun allGranted(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it } && allGranted()
    }

    // Re-check on first composition so permissions granted in the system
    // settings while the app was backgrounded are picked up immediately.
    LaunchedEffect(Unit) { granted = allGranted() }

    if (granted) {
        content()
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permission required",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                Text("Grant Permission")
            }
        }
    }
}
