plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nexusstream.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexusstream.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Fixed signing key (committed on purpose: personal, sideloaded app). Every CI build is signed
    // with the same key, so a new APK installs over the old one instead of needing an uninstall.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/nexusstream.p12")
            storeType = "pkcs12"
            storePassword = "nexusstream"
            keyAlias = "nexusstream"
            keyPassword = "nexusstream"
        }
    }

    buildTypes {
        release {
            // R8 shrinking is opt-in (./gradlew assembleRelease -Pminify=true) until it has been tried
            // on a device; the default release build is unchanged.
            isMinifyEnabled = project.findProperty("minify") == "true"
            isShrinkResources = project.findProperty("minify") == "true"
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose & Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // AndroidX Media3 Suite
    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX Media (legacy notification support)
    implementation("androidx.media:media:1.7.0")

    // Real YouTube / YouTube Music data + stream resolution (no API key needed)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Installs the baseline profile (src/main/baseline-prof.txt) for faster startup
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (the Android one is a stub on the JVM).
    testImplementation("org.json:json:20240303")

    // Google Cast (default media receiver). MediaRouteButton needs AppCompat resources.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Background work: upload checks, reminders, downloads
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Instrumented UI tests (run on a device / emulator: ./gradlew connectedDebugAndroidTest)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Coil Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")
}
