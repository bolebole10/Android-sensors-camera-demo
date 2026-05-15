package com.example.sensorscamerademo.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sensorscamerademo.ui.DemoScaffold
import com.example.sensorscamerademo.ui.ExplanationCard
import kotlin.math.sqrt

/**
 * Shake-to-clear demo. Uses TYPE_LINEAR_ACCELERATION, a fused virtual
 * sensor that subtracts gravity from the raw accelerometer signal. A
 * still device therefore reads near zero on all axes, which makes
 * shake detection a simple threshold check.
 *
 * To avoid counting one physical shake as several events, we ignore any
 * spike that happens within the 500 ms cooldown after the previous one.
 */
@Composable
fun ShakeScreen(onBack: () -> Unit) {
    DemoScaffold(title = "Shake to Clear", onBack = onBack) { modifier ->
        if (!hasSensor(Sensor.TYPE_LINEAR_ACCELERATION)) {
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
        var count by remember { mutableIntStateOf(0) }
        var lastShakeAt by remember { mutableLongStateOf(0L) }

        DisposableEffect(Unit) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val (x, y, z) = event.values
                    val magnitude = sqrt(x * x + y * y + z * z)
                    val now = System.currentTimeMillis()
                    if (magnitude > SHAKE_THRESHOLD && now - lastShakeAt > COOLDOWN_MS) {
                        count += 1
                        lastShakeAt = now
                        vibrate(context)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            onDispose { manager.unregisterListener(listener) }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Shakes detected",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = { count = 0 }) { Text("Reset") }

            ExplanationCard(
                text = "Linear acceleration excludes gravity, so a still device reads near " +
                    "zero on all axes. We detect a shake by checking when total acceleration " +
                    "exceeds a threshold (${SHAKE_THRESHOLD.toInt()} m/s²), with a 500 ms " +
                    "cooldown so one motion doesn't get counted multiple times."
            )
        }
    }
}

private const val SHAKE_THRESHOLD = 12f
private const val COOLDOWN_MS = 500L

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    // VibratorManager replaced Vibrator on Android 12 (API 31). We pick
    // the right one for the platform, and use VibrationEffect for the
    // short haptic tap on API 26+.
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(60L)
    }
}
