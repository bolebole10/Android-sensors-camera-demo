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

// Reusable helper — registrira senzor dok je ekran vidljiv, odjavi kad ode
@Composable
fun rememberSensorValues(sensorType: Int): State<FloatArray?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<FloatArray?>(null) }

    DisposableEffect(sensorType) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(sensorType)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // copyOf() obavezan — Android reciklira isti array između poziva
                state.value = event.values.copyOf()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }

        // Odjavi listener kad composable ode s ekrana (štedi bateriju)
        onDispose { manager.unregisterListener(listener) }
    }
    return state
}

// Vraća true ako uređaj ima traženi senzor
@Composable
fun hasSensor(sensorType: Int): Boolean {
    val context = LocalContext.current
    return remember(sensorType) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager.getDefaultSensor(sensorType) != null
    }
}

// Low-pass filter za glađenje šumovitih vrijednosti (alpha = koliko težine dajemo novom uzorku)
fun lowPass(previous: Float, sample: Float, alpha: Float = 0.15f): Float =
    previous + alpha * (sample - previous)

// Low-pass za kutove — rješava problem prijelaza 359°→1° (prosjek mora biti 0°, ne 180°)
fun lowPassAngle(previousDeg: Float, sampleDeg: Float, alpha: Float = 0.15f): Float {
    var diff = sampleDeg - previousDeg
    // Korekcija za "kratki put" oko kruga
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    val next = previousDeg + alpha * diff
    // Osiguraj raspon 0-360° (dupli modulo jer % može vratiti negativan broj)
    return (next % 360f + 360f) % 360f
}
