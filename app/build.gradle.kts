plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) apply false
}

// Local-only VPN remains usable without a Firebase project. Never fabricate project credentials.
val firebaseConfigured = file("google-services.json").isFile
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.example.h25vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.h25vpn.bxtkp"
        minSdk = 24
        targetSdk = 36
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "OAUTH_SIGNING_SHA1", "\"${System.getenv("OAUTH_SIGNING_SHA1") ?: ""}\"")
        buildConfigField("String", "OAUTH_SIGNING_SHA256", "\"${System.getenv("OAUTH_SIGNING_SHA256") ?: ""}\"")
        versionCode = 21
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "${rootDir}/debug.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "android"
        }
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += listOf(
                "**/libgojni.so",
                "**/libh1.so",
                "**/libovpncli.so",
                "**/libpdnsd.so",
                "**/libtun2socks.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    testImplementation("org.json:json:20240303")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
