import java.net.URI

plugins {
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val apiBaseUrl = providers.gradleProperty("kirakira.apiBaseUrl")
    .orElse("https://rosales.kirakira.moe/").get().trim().let { it.trimEnd('/') + "/" }
val apiUri = URI(apiBaseUrl)
require(apiUri.scheme == "https" && !apiUri.host.isNullOrBlank() && apiUri.userInfo == null &&
    apiUri.query == null && apiUri.fragment == null) {
    "kirakira.apiBaseUrl must be an HTTPS URL without credentials, query, or fragment"
}
val cryptoCheck = providers.gradleProperty("kirakira.cryptoCheck").map { it.toBooleanStrict() }.orElse(false).get()
val authUiCheck = providers.gradleProperty("kirakira.authUiCheck").map { it.toBooleanStrict() }.orElse(false).get()
require(!cryptoCheck || !authUiCheck) { "Select one isolated instrumentation variant at a time" }

android {
    namespace = "moe.kirakira"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "moe.kirakira"
        minSdk = 27
        targetSdk = 37
        versionCode = 2
        versionName = "0.2"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("boolean", "SYSTEM_CREDENTIALS_ENABLED", (apiBaseUrl == "https://rosales.kirakira.moe/").toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("cryptoCheck") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".cryptocheck"
            matchingFallbacks += "debug"
            buildConfigField("String", "API_BASE_URL", "\"https://auth.example.invalid/\"")
            buildConfigField("boolean", "SYSTEM_CREDENTIALS_ENABLED", "false")
        }
        create("authUiCheck") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".authuicheck"
            matchingFallbacks += "debug"
            buildConfigField("String", "API_BASE_URL", "\"https://auth.example.invalid/\"")
            buildConfigField("boolean", "SYSTEM_CREDENTIALS_ENABLED", "false")
        }
        release {
            optimization {
                enable = false
            }
        }
        create("performance") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            optimization {
                enable = true
            }
            buildConfigField("boolean", "SYSTEM_CREDENTIALS_ENABLED", "false")
        }
    }
    testBuildType = when {
        cryptoCheck -> "cryptoCheck"
        authUiCheck -> "authUiCheck"
        else -> "debug"
    }
    if (cryptoCheck || authUiCheck) {
        sourceSets.getByName("androidTest").apply {
            java.directories.clear()
            kotlin.directories.apply {
                clear()
                add(if (cryptoCheck) "src/cryptoCheckAndroidTest/java" else "src/authUiCheckAndroidTest/java")
                if (authUiCheck) add("src/authTestShared/java")
            }
        }
    }
    sourceSets.getByName("test").kotlin.directories.add("src/authTestShared/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.okhttp)
    implementation(libs.image.cropper)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.coil3)
    implementation(libs.coil.compose)
    implementation(libs.material.kolor)
    implementation(libs.compose.colorpicker)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

aboutLibraries {
    collect {
        configPath = rootProject.file("third_party/aboutlibraries")
        includePlatform = false
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
}
