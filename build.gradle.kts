// Top-level Gradle file. Plugins are declared here with `apply false` so
// they can be applied in the module-level build files below.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
