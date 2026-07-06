plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.receiptscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.receiptscanner"
        // minSdk 30 (Android 11) مقصودة: هي أول نسخة فيها Environment.isExternalStorageManager()
        // و ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION و FileObserver(File, Int) الحديث،
        // فهذا يبسّط الكود ويجنّبنا فحوصات SDK_INT متفرقة.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // قراءة النصوص من الصور (OCR) - لاتيني/إنجليزي فقط، راجع README
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // تخزين البيانات كـ JSON قبل تشفيرها
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // طلبات HTTP لاستدعاء Claude API (خيار السحابة الاحتياطي للعربية)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // رسوم بيانية لشاشة التحليلات
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Room 2.6.1 عمداً (وليس 3.0 الأحدث) - يدعم SupportSQLiteOpenHelper.Factory
    // مباشرة اللازمة لتكامل SQLCipher بدون طبقة توافق إضافية
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // تشفير قاعدة البيانات - النسخة "الكلاسيكية" الموثَّقة بشكل أوسع (راجع تعليق AppDatabase.kt)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.3.1")
}
