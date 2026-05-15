package com.example.sensorscamerademo.sensors

import android.hardware.Sensor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.sensorscamerademo.ui.DemoScaffold
import com.example.sensorscamerademo.ui.ExplanationCard
import kotlin.math.abs

/**
 * Bubble level demo. Uses the gravity sensor — it reports the direction
 * of gravity in the device's coordinate system, so when the phone is
 * flat on a table the X and Y components are near zero. Falling back to
 * the raw accelerometer would also work (gravity is just a low-pass
 * filtered accelerometer), but the gravity sensor handles that filter
 * for us.
 */
@Composable
fun BubbleLevelScreen(onBack: () -> Unit) {
    DemoScaffold(title = "Bubble Level", onBack = onBack) { modifier ->
        val hasGravity = hasSensor(Sensor.TYPE_GRAVITY)
        // Fall back to the accelerometer on devices without a synthetic
        // gravity sensor. The maths is the same; the accelerometer values
        // are just noisier and include user motion as well as gravity.
        val sensorType = if (hasGravity) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER
        val values by rememberSensorValues(sensorType)

        if (!hasSensor(sensorType)) {
            UnsupportedSensorMessage(modifier)
            return@DemoScaffold
        }

        val x = values?.getOrNull(0) ?: 0f
        val y = values?.getOrNull(1) ?: 0f
        val z = values?.getOrNull(2) ?: 0f

        val isLevel = abs(x) < 0.3f && abs(y) < 0.3f
        val bubbleColor by animateColorAsState(
            targetValue = if (isLevel) Color(0xFF2E7D32) else Color(0xFFC62828),
            animationSpec = tween(durationMillis = 250),
            label = "bubble-color"
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BubbleLevelCanvas(
                gravityX = x,
                gravityY = y,
                bubbleColor = bubbleColor,
                outlineColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Text(
                text = "X: %.2f   Y: %.2f   Z: %.2f".format(x, y, z),
                style = MaterialTheme.typography.titleMedium
            )

            ExplanationCard(
                text = "The gravity sensor reports the direction of gravity in the device's " +
                    "coordinate system. When flat on a table, X and Y are near zero and Z is " +
                    "near 9.81. We map the X/Y components to a bubble position on screen."
            )
        }
    }
}

@Composable
private fun BubbleLevelCanvas(
    gravityX: Float,
    gravityY: Float,
    bubbleColor: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 8.dp.toPx()

        // Outer circle and crosshairs.
        drawCircle(
            color = outlineColor.copy(alpha = 0.4f),
            radius = radius,
            center = centre,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = outlineColor.copy(alpha = 0.4f),
            radius = radius * 0.25f,
            center = centre,
            style = Stroke(width = 2f)
        )
        drawLine(
            color = outlineColor.copy(alpha = 0.3f),
            start = Offset(centre.x - radius, centre.y),
            end = Offset(centre.x + radius, centre.y),
            strokeWidth = 1.5f
        )
        drawLine(
            color = outlineColor.copy(alpha = 0.3f),
            start = Offset(centre.x, centre.y - radius),
            end = Offset(centre.x, centre.y + radius),
            strokeWidth = 1.5f
        )

        // Normalize gravity into a -1..1 range. Earth gravity is ~9.81 m/s²
        // along whichever axis is "down" — when the phone is tilted on its
        // side, the X or Y reading approaches that magnitude, which we
        // treat as the edge of the dial.
        val nx = (gravityX / 9.81f).coerceIn(-1f, 1f)
        val ny = (gravityY / 9.81f).coerceIn(-1f, 1f)

        // Compose's Y axis points down, but the gravity sensor's Y axis
        // points "up" out of the bottom of the phone. We flip the sign of
        // X so tilting the right side down moves the bubble right.
        val bubble = Offset(
            x = centre.x - nx * radius * 0.7f,
            y = centre.y + ny * radius * 0.7f
        )
        drawCircle(color = bubbleColor, radius = radius * 0.15f, center = bubble)
    }
}

@Composable
private fun UnsupportedSensorMessage(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "This sensor is not available on this device.",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
