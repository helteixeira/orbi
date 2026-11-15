plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Assinatura: se existir uma chave privada (segredo KEYSTORE_B64 no GitHub) usa essa.
// Sem ela, usa a chave de testes da máquina que compila.
val ficheiroChave: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "app.wristmind.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.helteixeira.orbi"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
    }

    signingConfigs {
        if (ficheiroChave != null && file(ficheiroChave).exists()) {
            create("publicacao") {
                storeFile = file(ficheiroChave)
                storePassword = "wristmind"
                keyAlias = "wristmind"
                keyPassword = "wristmind"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("publicacao") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
