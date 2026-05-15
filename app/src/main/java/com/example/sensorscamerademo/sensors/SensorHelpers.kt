package com.example.sensorscamerademo.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Subscribes to a sensor for as long as the calling composable is in the
 * composition. Mirrors the classic Activity onResume/onPause pair:
 *
 *  - The listener is registered when the composable enters composition,
 *    which corresponds to a screen becoming visible.
 *  - The listener is unregistered in [DisposableEffect]'s onDispose block
 *    when the composable leaves composition (screen hidden, navigated
 *    away, etc.), so the sensor stops draining the battery.
 *
 * This pattern is the same in every sensor demo in this app — point it
 * out during the presentation.
 *
 * Returns `null` if the device does not have the requested sensor, so
 * callers must handle the unsupported case.
 */
@Composable
fun rememberSensorValues(sensorType: Int): State<FloatArray?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<FloatArray?>(null) }

    DisposableEffect(sensorType) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(sensorType)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // copyOf() because the framework reuses the same array between
                // callbacks — storing the reference would mean the value silently
                // mutates underneath us.
                state.value = event.values.copyOf()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Not used in these demos. Real apps may want to react to
                // SENSOR_STATUS_UNRELIABLE for the compass, etc.
            }
        }
        sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose { manager.unregisterListener(listener) }
    }
    return state
}

/**
 * Returns true if the device exposes the given sensor type.
 */
@Composable
fun hasSensor(sensorType: Int): Boolean {
    val context = LocalContext.current
    return remember(sensorType) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager.getDefaultSensor(sensorType) != null
    }
}

/**
 * Simple first-order low-pass filter, used to smooth noisy sensor readings
 * such as the compass azimuth. `alpha` is how much weight to give the new
 * sample (0.0 = ignore new samples, 1.0 = no smoothing).
 *
 * For angles wrapping at 360° (the compass) we need to handle the wrap-around
 * — e.g. averaging 359° and 1° should yield 0°, not 180°. [lowPassAngle]
 * does that; [lowPass] is for plain scalar values.
 */
fun lowPass(previous: Float, sample: Float, alpha: Float = 0.15f): Float =
    previous + alpha * (sample - previous)

fun lowPassAngle(previousDeg: Float, sampleDeg: Float, alpha: Float = 0.15f): Float {
    var diff = sampleDeg - previousDeg
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    val next = previousDeg + alpha * diff
    return (next % 360f + 360f) % 360f
}
