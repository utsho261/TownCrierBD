import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""
val cloudinaryCloudName: String = localProperties.getProperty("CLOUDINARY_CLOUD_NAME") ?: ""
val cloudinaryUploadPreset: String = localProperties.getProperty("CLOUDINARY_UPLOAD_PRESET") ?: ""
val serverUrl: String = localProperties.getProperty("SERVER_URL") ?: "https://tcbd-server.onrender.com"

android {
    namespace = "com.example.towncrierbd"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.towncrierbd"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "2.2"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    sourceSets.getByName("main").assets.srcDir(rootProject.file(".offline-voice/assets"))
    androidResources { noCompress += "onnx" }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("towncrierbd-release.jks")
            storePassword = "towncrierbd123"
            keyAlias = "towncrierbd"
            keyPassword = "towncrierbd123"
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Models are build dependencies; recognition never calls a paid speech service.
val voiceCache = rootProject.file(".offline-voice")
val voiceRuntime = rootProject.file(".offline-voice/runtime/sherpa-onnx-2.0.1.aar")
val prepareOfflineVoice = tasks.register<JavaExec>("prepareOfflineVoice") {
    group = "build setup"
    description = "Download and verify free Bengali/English models for offline voice"
    mainClass.set(rootProject.file("tools/PrepareOfflineVoice.java").absolutePath)
    args(rootProject.projectDir.absolutePath)
    if (gradle.startParameter.isOffline) args("--offline")
    inputs.files(rootProject.file("tools/PrepareOfflineVoice.java"), rootProject.file("tools/offline-voice-manifest.tsv"))
    outputs.dir(voiceCache)
    outputs.upToDateWhen { false }
}
tasks.named("preBuild").configure { dependsOn(prepareOfflineVoice) }

dependencies {
    implementation(files(voiceRuntime).builtBy(prepareOfflineVoice))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.20")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
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

    testImplementation("junit:junit:4.13.2")
}