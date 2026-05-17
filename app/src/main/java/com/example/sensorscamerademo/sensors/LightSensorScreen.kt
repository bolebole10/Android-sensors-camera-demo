package com.example.sensorscamerademo.sensors

import android.hardware.Sensor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sensorscamerademo.ui.DemoScaffold

@Composable
fun LightSensorScreen(onBack: () -> Unit) {
    DemoScaffold(title = "Light → Auto Theme", onBack = onBack) { modifier ->
        if (!hasSensor(Sensor.TYPE_LIGHT)) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("This sensor is not available on this device.")
            }
            return@DemoScaffold
        }

        // TYPE_LIGHT daje osvjetljenje u luxima (samo values[0])
        val values by rememberSensorValues(Sensor.TYPE_LIGHT)
        val lux = values?.getOrNull(0) ?: 0f
        // Ispod 50 lux = tamno, iznad = svijetlo
        val isDark = lux < LUX_THRESHOLD

        // animateColorAsState → glatki prijelaz boja (400 ms) umjesto nagle promjene
        val background by animateColorAsState(
            targetValue = if (isDark) Color(0xFF101418) else Color(0xFFF7F4EE),
            animationSpec = tween(400),
            label = "bg"
        )
        val foreground by animateColorAsState(
            targetValue = if (isDark) Color(0xFFEDEDED) else Color(0xFF1B1B1B),
            animationSpec = tween(400),
            label = "fg"
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%.0f lux".format(lux),
                style = MaterialTheme.typography.displayLarge,
                color = foreground
            )
            Text(
                text = if (isDark) "Dark Mode" else "Light Mode",
                style = MaterialTheme.typography.headlineSmall,
                color = foreground
            )
            Text(
                text = "Threshold: $LUX_THRESHOLD lux",
                style = MaterialTheme.typography.bodyMedium,
                color = foreground.copy(alpha = 0.7f)
            )

        }
    }
}

private const val LUX_THRESHOLD = 50f
