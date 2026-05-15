package com.example.sensorscamerademo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.sensorscamerademo.navigation.AppNavigation
import com.example.sensorscamerademo.ui.theme.SensorsCameraDemoTheme

/**
 * The single Activity for the whole app. Everything else is a Composable
 * inside the [AppNavigation] graph. This is the "single-activity" pattern
 * recommended for modern Android apps — it makes navigation, deep links,
 * and back-stack handling much simpler than multiple activities.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SensorsCameraDemoTheme {
                AppNavigation()
            }
        }
    }
}
