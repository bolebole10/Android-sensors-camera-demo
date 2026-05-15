package com.example.sensorscamerademo.navigation

/**
 * Routes for the Sensors tab. Defined as plain string constants so the
 * navigation code stays small and easy to follow during the presentation.
 *
 * `Menu` is the start destination — the list of demo cards. The other
 * routes are pushed on top of it when the user taps a card.
 */
object SensorRoutes {
    const val Menu = "sensors/menu"
    const val BubbleLevel = "sensors/bubble-level"
    const val Compass = "sensors/compass"
    const val Shake = "sensors/shake"
    const val Light = "sensors/light"
}
