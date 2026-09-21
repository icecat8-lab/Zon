plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zon.filemanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zon.filemanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 1. เปิดใช้งาน Jetpack Compose (แก้ไข Unresolved reference: compose / Material3)[span_6](start_span)[span_6](end_span)
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX Core & Activity
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // 2. Jetpack Compose & Material 3 Dependencies (แก้ไข Unresolved reference ใน Screen ต่างๆ)[span_7](start_span)[span_7](end_span)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 3. Apache Commons Compress & IO (แก้ไข Unresolved reference ใน ArchivePreview.kt)[span_8](start_span)[span_8](end_span)
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("org.tukaani:xz:1.9") // รองรับไฟล์ .xz

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
