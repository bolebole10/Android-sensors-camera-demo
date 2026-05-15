package com.example.sensorscamerademo.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensorscamerademo.ui.DemoScaffold
import com.example.sensorscamerademo.ui.ExplanationCard
import kotlin.math.PI

/**
 * Compass demo. Uses TYPE_ROTATION_VECTOR (a fused virtual sensor that
 * combines accelerometer, gyroscope and magnetometer) and converts the
 * rotation vector into an azimuth angle in degrees.
 *
 * We apply a low-pass filter to the azimuth because the raw reading is
 * jittery — even when the phone is sitting still, the value tends to
 * wobble by a few degrees.
 */
@Composable
fun CompassScreen(onBack: () -> Unit) {
    DemoScaffold(title = "Compass", onBack = onBack) { modifier ->
        if (!hasSensor(Sensor.TYPE_ROTATION_VECTOR)) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("This sensor is not available on this device.")
            }
            return@DemoScaffold
        }

        val context = LocalContext.current
        // Smoothed azimuth in degrees, 0..360.
        var azimuth by remember { mutableFloatStateOf(0f) }

        // Custom DisposableEffect — we do the rotation-matrix math right
        // inside the SensorEventListener rather than going through the
        // generic rememberSensorValues helper, because we need to convert
        // every event before storing it.
        DisposableEffect(Unit) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // orientation[0] is the azimuth in radians, range -π..π.
                    val rawDeg = ((Math.toDegrees(orientation[0].toDouble())
                        .toFloat()) + 360f) % 360f
                    azimuth = lowPassAngle(azimuth, rawDeg, alpha = 0.15f)
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            onDispose { manager.unregisterListener(listener) }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CompassDial(
                azimuthDeg = azimuth,
                primary = MaterialTheme.colorScheme.primary,
                onSurface = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Text(
                text = "%.0f° %s".format(azimuth, cardinal(azimuth)),
                style = MaterialTheme.typography.headlineMedium
            )

            ExplanationCard(
                text = "TYPE_ROTATION_VECTOR is a fused, virtual sensor — it combines the " +
                    "accelerometer, gyroscope, and magnetometer to give a stable orientation. " +
                    "We extract the azimuth (rotation around the vertical axis), convert it to " +
                    "degrees, and rotate the dial. A simple low-pass filter smooths the jitter."
            )
        }
    }
}

@Composable
private fun CompassDial(
    azimuthDeg: Float,
    primary: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 24.dp.toPx()

        // The trick: rotate the whole dial by -azimuth so North always
        // ends up at the top of the screen, while the arrow at the top
        // stays still — effectively the dial spins, the arrow does not.
        rotate(degrees = -azimuthDeg, pivot = centre) {
            // Outer ring.
            drawCircle(
                color = onSurface.copy(alpha = 0.5f),
                radius = radius,
                center = centre,
                style = Stroke(width = 3f)
            )

            // Tick marks every 30°.
            for (i in 0 until 12) {
                val angleRad = i * 30 * (PI / 180f)
                val cos = kotlin.math.cos(angleRad).toFloat()
                val sin = kotlin.math.sin(angleRad).toFloat()
                val outer = Offset(
                    centre.x + cos * radius,
                    centre.y + sin * radius
                )
                val inner = Offset(
                    centre.x + cos * (radius - 14.dp.toPx()),
                    centre.y + sin * (radius - 14.dp.toPx())
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.6f),
                    start = inner,
                    end = outer,
                    strokeWidth = 2.5f
                )
            }

            // Cardinal labels (N, E, S, W) — drawn through the platform
            // canvas because Compose's drawText needs a TextMeasurer and
            // we want to keep this demo simple.
            val labels = listOf("N" to -90f, "E" to 0f, "S" to 90f, "W" to 180f)
            val paint = android.graphics.Paint().apply {
                color = if (onSurface == Color.White) android.graphics.Color.WHITE
                else android.graphics.Color.DKGRAY
                textSize = 18.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            // North label gets the primary color so it stands out.
            val northPaint = android.graphics.Paint(paint).apply {
                color = android.graphics.Color.argb(
                    (primary.alpha * 255).toInt(),
                    (primary.red * 255).toInt(),
                    (primary.green * 255).toInt(),
                    (primary.blue * 255).toInt()
                )
            }
            labels.forEach { (text, angleDeg) ->
                val angleRad = angleDeg * (PI / 180f)
                val tx = centre.x + kotlin.math.cos(angleRad).toFloat() * (radius - 36.dp.toPx())
                val ty = centre.y + kotlin.math.sin(angleRad).toFloat() * (radius - 36.dp.toPx()) +
                    paint.textSize / 3f
                drawContext.canvas.nativeCanvas.drawText(
                    text, tx, ty, if (text == "N") northPaint else paint
                )
            }
        }

        // The red north-arrow stays fixed at the top of the dial regardless
        // of rotation, because we draw it outside the rotate() block.
        val arrowTip = Offset(centre.x, centre.y - radius * 0.85f)
        val arrowBase1 = Offset(centre.x - 14.dp.toPx(), centre.y)
        val arrowBase2 = Offset(centre.x + 14.dp.toPx(), centre.y)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(arrowTip.x, arrowTip.y)
            lineTo(arrowBase1.x, arrowBase1.y)
            lineTo(arrowBase2.x, arrowBase2.y)
            close()
        }
        drawPath(path = path, color = Color(0xFFE53935))
        drawCircle(color = onSurface, radius = 6.dp.toPx(), center = centre)
    }
}

private fun cardinal(deg: Float): String = when {
    deg < 22.5f || deg >= 337.5f -> "N"
    deg < 67.5f -> "NE"
    deg < 112.5f -> "E"
    deg < 157.5f -> "SE"
    deg < 202.5f -> "S"
    deg < 247.5f -> "SW"
    deg < 292.5f -> "W"
    else -> "NW"
}
