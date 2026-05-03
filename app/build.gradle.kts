plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localai.chatbot"
    compileSdk = 34

    androidResources {
        // Keep GGUF models uncompressed in APK assets (faster first-run install, less CPU).
        noCompress += "gguf"
    }

    // Add generated asset dir for optional bundled models (keeps repo clean).
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/bundledModels"))

    defaultConfig {
        applicationId = "com.localai.chatbot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            ndk {
                // Include emulator ABI so we can run tests without a phone.
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }
        release {
            isMinifyEnabled = false
            // Sign release with debug key so APK is directly installable for sharing/testing.
            signingConfig = signingConfigs.getByName("debug")
            ndk {
                // Phone-targeted build: keep only modern Android phone ABI.
                abiFilters.addAll(listOf("arm64-v8a"))
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation(project(":ui"))
    implementation(project(":viewmodel"))
    implementation(project(":core"))
    
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.5.0")
}

// Optional: bundle SmolLM2 Mini into the APK so the app works offline without downloading.
// If the file is missing, the build still succeeds and the app will fall back to download.
val bundledModelsDir = layout.buildDirectory.dir("generated/bundledModels/models")
val copyBundledSmolLm2 by tasks.registering(Copy::class) {
    val src = rootProject.file("_models/SmolLM2-360M-Instruct-Q4_K_M.gguf")
    onlyIf { src.exists() }
    from(src)
    into(bundledModelsDir)
}

// Optional: bundle TinyLlama too (very large).
val copyBundledTinyLlama by tasks.registering(Copy::class) {
    val src = rootProject.file("_models/tinyllama-1.1b-chat.Q4_K_M.gguf")
    onlyIf { src.exists() }
    from(src)
    into(bundledModelsDir)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copyBundledSmolLm2)
}



