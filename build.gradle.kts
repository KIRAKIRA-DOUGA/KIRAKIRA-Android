// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("verify") {
    group = "verification"
    description = "Builds the debug app and runs unit tests and Android Lint."
    dependsOn(":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug")
}
