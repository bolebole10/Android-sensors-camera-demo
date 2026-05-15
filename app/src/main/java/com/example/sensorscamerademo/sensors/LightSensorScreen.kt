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

/**
 * Light sensor demo. Reads ambient lux and uses it to swap the entire
 * screen between a light and a dark color scheme. The transition uses
 * [animateColorAsState] so the swap is smooth instead of a hard cut.
 *
 * Note: TYPE_LIGHT only delivers events when the lux value actually
 * changes — so if the surroundings are stable the listener may fire
 * once and then go quiet. That is the sensor working correctly.
 */
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

        val values by rememberSensorValues(Sensor.TYPE_LIGHT)
        val lux = values?.getOrNull(0) ?: 0f
        val isDark = lux < LUX_THRESHOLD

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

            // We can't use ExplanationCard here because it pulls colors
            // from MaterialTheme — and on this screen the whole point is
            // that we're animating colors independently of the theme.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = foreground.copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About this demo",
                        style = MaterialTheme.typography.titleSmall,
                        color = foreground
                    )
                    Text(
                        text = "The ambient light sensor reports the brightness striking the " +
                            "front of the device in lux. We compare it against a threshold and " +
                            "swap the colors. animateColorAsState gives us the smooth fade " +
                            "between the two color sets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = foreground.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

private const val LUX_THRESHOLD = 50f
