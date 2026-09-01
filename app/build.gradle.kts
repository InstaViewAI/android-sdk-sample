import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
}

/**
 * Firebase is a prerequisite of the SDK, but the sample must still configure and compile for
 * anyone who has not dropped in their own `google-services.json` yet.
 */
val hasFirebaseConfig = file("google-services.json").exists()

if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

/** Credentials the SDK needs at runtime, supplied through `local.properties` (never committed). */
val sdkCredentials = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Reads an SDK credential, falling back to an empty string so the build still configures. */
fun credential(key: String): String = sdkCredentials.getProperty(key).orEmpty()

/** Shared debug keystore, so every developer's debug build carries the same certificate. */
val debugKeystore = rootProject.file("keys/debug_key")

/**
 * Whether the shared keystore and its `local.properties` credentials are both present. When they
 * are not, the debug build falls back to the standard `~/.android/debug.keystore`, so a fresh
 * clone still builds; only its signature differs.
 */
val hasDebugSigning = debugKeystore.exists() &&
    credential("instavision.debugStorePassword").isNotEmpty() &&
    credential("instavision.debugKeyAlias").isNotEmpty()

android {
    namespace = "ai.instavision.sandbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ai.instavision.sandbox"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("Boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfig.toString())
        buildConfigField("String", "PARTNER_ID", "\"${credential("instavision.partnerId")}\"")
        buildConfigField("String", "CLIENT_ID", "\"${credential("instavision.clientId")}\"")
    }
    signingConfigs {
        getByName("debug") {
            if (hasDebugSigning) {
                storeFile = debugKeystore
                storePassword = credential("instavision.debugStorePassword")
                keyAlias = credential("instavision.debugKeyAlias")
                keyPassword = credential("instavision.debugKeyPassword")
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_19
        targetCompatibility = JavaVersion.VERSION_19
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_19)
    }
}

dependencies {
    implementation(libs.guardian)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.zxing.embedded)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
