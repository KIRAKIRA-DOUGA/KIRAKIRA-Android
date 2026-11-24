import java.net.URI

plugins {
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
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
val imageDeliveryBaseUrl = when (providers.gradleProperty("kirakira.imageProvider").orElse("cloudflare-prod").get()) {
    "cloudflare-prod" -> "https://kirafile.com/cdn-cgi/imagedelivery/Gyz90amG54C4b_dtJiRpYg/"
    "cloudflare-stg" -> "https://kirakira-image.starcitizen.rip/cdn-cgi/imagedelivery/nLUFLrrgVwzQdEx8eL5BaA/"
    else -> error("kirakira.imageProvider must be cloudflare-prod or cloudflare-stg")
}

val appVersionName = "0.4"
val distributionVersionCode = providers.gradleProperty("kirakira.distributionVersionCode").map { it.toInt() }
val ciRunNumber = providers.gradleProperty("kirakira.ciRunNumber").map { it.toInt() }
val releaseVersionName = providers.gradleProperty("kirakira.releaseVersionName")

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
        versionCode = 3
        versionName = appVersionName
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "IMAGE_DELIVERY_BASE_URL", "\"$imageDeliveryBaseUrl\"")
        buildConfigField("boolean", "SYSTEM_CREDENTIALS_ENABLED", (apiBaseUrl == "https://rosales.kirakira.moe/").toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
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
        // Baseline Profile generation installs these plugin-created variants; release stays unsigned.
        matching { it.name.startsWith("nonMinified") || it.name.startsWith("benchmark") }.configureEach {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Vendored Number Flow sources; see third_party/number-flow/README.md.
    sourceSets.getByName("main").kotlin.directories.add(rootProject.file("third_party/number-flow/src").path)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

androidComponents {
    // The Baseline Profile plugin copies release's optimization block into the build types it records
    // with; profiles must be recorded against unobfuscated names, so turn R8 off after it runs.
    finalizeDsl { android ->
        android.buildTypes.matching { it.name.startsWith("nonMinified") }.configureEach {
            optimization {
                enable = false
            }
        }
    }
    onVariants(selector().withBuildType("release")) { variant ->
        val output = variant.outputs.single()
        require(!(ciRunNumber.isPresent && releaseVersionName.isPresent)) {
            "kirakira.ciRunNumber and kirakira.releaseVersionName cannot be used together"
        }
        distributionVersionCode.orNull?.let { output.versionCode.set(it) }
        ciRunNumber.orNull?.let { output.versionName.set("$appVersionName-ci.$it") }
        releaseVersionName.orNull?.let {
            require(Regex("""\d+\.\d+(\.\d+)?""").matches(it)) { "kirakira.releaseVersionName must be a numeric version" }
            output.versionName.set(it)
        }
        output.outputFileName.set(output.versionName.map { "KIRAKIRA-$it-release.apk" })
    }
}

dependencies {
    // Compose and Material
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.adaptive.core)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX platform, lifecycle and navigation
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Network and serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Media playback
    implementation(libs.media3.database)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.image.cropper)
    implementation(libs.telephoto.coil3)

    // UI utilities
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.compose.colorpicker)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.material.kolor)
    implementation(libs.zxing.core)

    // Local JVM tests (src/test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests (src/androidTest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

baselineProfile {
    // One committed profile serves release and performance; normal builds never start generation.
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}

aboutLibraries {
    collect {
        configPath = rootProject.file("third_party/aboutlibraries")
        includePlatform = false
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
}
