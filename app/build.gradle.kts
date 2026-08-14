plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.fsstructure.creator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fsstructure.creator"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Support the broadest practical range of Android CPU architectures
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    // Sign the APK so Android allows installation
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "password"
            keyAlias = "release"
            keyPassword = "password"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// --- AUTOMATIC ICON GENERATOR ---
tasks.register<Copy>("generateAppIcon") {
    from(rootProject.file("icon-source.png"))
    into(layout.projectDirectory.dir("src/main/res/mipmap-xxxhdpi"))
    rename { "ic_launcher.png" }
}
tasks.named("preBuild") {
    dependsOn("generateAppIcon")
}
// --------------------------------

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Material Icons Extended (Required for Editor UI icons like Folder, Description, NoteAdd)
    // R8 strips unused icons in release builds to keep it lightweight.
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation (Lightweight)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database (Local saved conversations)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (AI API calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DataStore (Securely storing API key & Folder URI)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Debugging
    debugImplementation("androidx.compose.ui:ui-tooling")
}