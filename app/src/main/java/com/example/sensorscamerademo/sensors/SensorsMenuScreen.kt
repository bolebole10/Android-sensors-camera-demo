package com.example.sensorscamerademo.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Top-level Sensors menu. Each card opens a demo screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsMenuScreen(
    onOpenBubbleLevel: () -> Unit,
    onOpenCompass: () -> Unit,
    onOpenShake: () -> Unit,
    onOpenLight: () -> Unit
) {
    val items = listOf(
        DemoEntry(
            title = "Bubble Level",
            subtitle = "Gravity sensor — visualize device tilt",
            icon = Icons.Filled.Bolt,
            onClick = onOpenBubbleLevel
        ),
        DemoEntry(
            title = "Compass",
            subtitle = "Rotation vector — heading in degrees",
            icon = Icons.Filled.Explore,
            onClick = onOpenCompass
        ),
        DemoEntry(
            title = "Shake to Clear",
            subtitle = "Linear acceleration — detect shakes",
            icon = Icons.Filled.Vibration,
            onClick = onOpenShake
        ),
        DemoEntry(
            title = "Light → Auto Theme",
            subtitle = "Ambient light sensor flips the theme",
            icon = Icons.Filled.LightMode,
            onClick = onOpenLight
        )
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sensors") }) }
    ) { padding ->
        DemoList(items = items, padding = padding)
    }
}

@Composable
internal fun DemoList(items: List<DemoEntry>, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { entry ->
            DemoCard(entry)
        }
    }
}

@Composable
internal fun DemoCard(entry: DemoEntry) {
    Card(
        onClick = entry.onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal data class DemoEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
