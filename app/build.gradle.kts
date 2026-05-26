import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

// ✅ FIX: Read secrets from local.properties instead of hardcoding in source
// local.properties is already in .gitignore by default in Android projects
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.example.towncrierbd"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.towncrierbd"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ✅ Inject secrets as BuildConfig fields — accessible at runtime, not in source
        buildConfigField("String", "MAPS_API_KEY",
            "\"${localProps["MAPS_API_KEY"] ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME",
            "\"${localProps["CLOUDINARY_CLOUD_NAME"] ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET",
            "\"${localProps["CLOUDINARY_UPLOAD_PRESET"] ?: ""}\"")
        buildConfigField("String", "SERVER_URL",
            "\"${localProps["SERVER_URL"] ?: ""}\"")

        // ✅ Also inject Maps API key for AndroidManifest placeholder
        manifestPlaceholders["MAPS_API_KEY"] = localProps["MAPS_API_KEY"] ?: ""
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // ✅ Enable BuildConfig generation (disabled by default in AGP 8+)
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation(platform("com.google.firebase:firebase-bom:32.3.1"))
    implementation("com.google.firebase:firebase-auth:22.1.2")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.cloudinary:cloudinary-android:2.3.1")
}