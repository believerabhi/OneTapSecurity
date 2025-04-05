plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// build.gradle (app module)
//plugins {
//    id 'com.android.application'
//    id 'org.jetbrains.kotlin.android'
//}

//android {
//    compileSdk 34
//
//    defaultConfig {
//        applicationId "com.example.screenanalyzer"
//        minSdk 21
//        targetSdk 34
//        versionCode 1
//        versionName "1.0"
//    }
//
//    buildFeatures {
//        viewBinding true
//    }
//}



android {
    namespace = "com.onetap.security"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.onetap.security"
        minSdk = 26
        targetSdk = 35
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // ML Kit for text recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // Coroutines for asynchronous operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // TensorFlow Lite dependencies
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-task-text:0.4.4")
    
    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.0.0")
    testImplementation("org.mockito:mockito-inline:5.0.0")
    testImplementation("org.robolectric:robolectric:4.10.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}