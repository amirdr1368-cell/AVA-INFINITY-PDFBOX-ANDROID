plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.avainfinity.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.avainfinity.android"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":library"))
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Optional PDFBox JPEG-2000 codec, included at runtime for full JPX support.
    implementation("com.gemalto.jp2:jp2-android:1.0.3")
    testImplementation("junit:junit:4.13.2")
}
