package com.example.sensorscamerademo.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sensorscamerademo.camera.BarcodeScannerScreen
import com.example.sensorscamerademo.camera.Camera2ManualScreen
import com.example.sensorscamerademo.camera.CameraMenuScreen
import com.example.sensorscamerademo.camera.VideoRecordingScreen
import com.example.sensorscamerademo.sensors.BubbleLevelScreen
import com.example.sensorscamerademo.sensors.CompassScreen
import com.example.sensorscamerademo.sensors.LightSensorScreen
import com.example.sensorscamerademo.sensors.SensorsMenuScreen
import com.example.sensorscamerademo.sensors.ShakeScreen

/**
 * Top-level app shell: a bottom navigation bar that swaps between the
 * Sensors and Camera tabs. Each tab owns its own [NavHostController], so
 * navigating to a demo inside one tab and then switching tabs preserves
 * the inner back stack of each tab independently.
 *
 * This is the simplest pattern that still gives us per-tab back stacks
 * without pulling in heavier libraries.
 */
@Composable
fun AppNavigation() {
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Sensors) }
    val sensorsNav = rememberNavController()
    val cameraNav = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        // Two NavHosts kept in memory at once so each tab keeps its back
        // stack when the user switches tabs. Only the selected one is
        // composed visibly; the other is replaced by nothing on screen.
        when (selectedTab) {
            Tab.Sensors -> SensorsNavHost(sensorsNav, Modifier.padding(padding))
            Tab.Camera -> CameraNavHost(cameraNav, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SensorsNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = SensorRoutes.Menu,
        modifier = modifier
    ) {
        composable(SensorRoutes.Menu) {
            SensorsMenuScreen(
                onOpenBubbleLevel = { navController.navigate(SensorRoutes.BubbleLevel) },
                onOpenCompass = { navController.navigate(SensorRoutes.Compass) },
                onOpenShake = { navController.navigate(SensorRoutes.Shake) },
                onOpenLight = { navController.navigate(SensorRoutes.Light) }
            )
        }
        composable(SensorRoutes.BubbleLevel) {
            BubbleLevelScreen(onBack = { navController.popBackStack() })
        }
        composable(SensorRoutes.Compass) {
            CompassScreen(onBack = { navController.popBackStack() })
        }
        composable(SensorRoutes.Shake) {
            ShakeScreen(onBack = { navController.popBackStack() })
        }
        composable(SensorRoutes.Light) {
            LightSensorScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun CameraNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = CameraRoutes.Menu,
        modifier = modifier
    ) {
        composable(CameraRoutes.Menu) {
            CameraMenuScreen(
                onOpenVideo = { navController.navigate(CameraRoutes.Video) },
                onOpenBarcode = { navController.navigate(CameraRoutes.Barcode) },
                onOpenCamera2 = { navController.navigate(CameraRoutes.Camera2Manual) }
            )
        }
        composable(CameraRoutes.Video) {
            VideoRecordingScreen(onBack = { navController.popBackStack() })
        }
        composable(CameraRoutes.Barcode) {
            BarcodeScannerScreen(onBack = { navController.popBackStack() })
        }
        composable(CameraRoutes.Camera2Manual) {
            Camera2ManualScreen(onBack = { navController.popBackStack() })
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Sensors("Sensors", Icons.Outlined.Sensors),
    Camera("Camera", Icons.Outlined.PhotoCamera);
}
