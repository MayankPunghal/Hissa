import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// local.properties is optional: without it the app asks for the Supabase URL + key on first launch.
val lp = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.hissa"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.hissa"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 2
        versionName = (project.findProperty("versionName") as String?) ?: "0.3"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField("String", "SUPABASE_URL", "\"${lp.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${lp.getProperty("SUPABASE_ANON_KEY", "")}\"")
        // Optional: your own invite page (see docs/join). Without it, invites use the hissa://join/CODE link.
        buildConfigField("String", "INVITE_BASE", "\"${lp.getProperty("INVITE_BASE", "")}\"")
        manifestPlaceholders["inviteHost"] = lp.getProperty("INVITE_HOST", "hissa.invalid")
        // Sample-data mode is for debug builds, or a release built with -Pdemo=true to test it.
        buildConfigField("boolean", "DEMO_OK", (project.findProperty("demo") == "true").toString())
    }
    // Release builds are signed with your own key (RELEASE_* in local.properties, see the README).
    // Without one, they fall back to the debug key so forks still build an installable APK.
    val hasKey = lp.getProperty("RELEASE_STORE_FILE") != null
    signingConfigs {
        if (hasKey) create("release") {
            storeFile = rootProject.file(lp.getProperty("RELEASE_STORE_FILE"))
            storePassword = lp.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = lp.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = lp.getProperty("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { buildConfigField("boolean", "DEMO_OK", "true") }
        release {
            signingConfig = signingConfigs.getByName(if (hasKey) "release" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.mlkit:text-recognition:16.0.1") // reads receipts on the phone, no network
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
