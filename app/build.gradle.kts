import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val tgApiId: Int = (System.getenv("TG_API_ID")
    ?: localProps.getProperty("tg.api.id")
    ?: "0").trim().toIntOrNull() ?: 0

val tgApiHash: String = (System.getenv("TG_API_HASH")
    ?: localProps.getProperty("tg.api.hash")
    ?: "").trim().replace("\"", "\\\"")

android {
    namespace = "com.tgaudiobooks.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tgaudiobooks.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 210
        versionName = "2.1.0"

        buildConfigField("int", "TG_API_ID", tgApiId.toString())
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHash\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "echogram"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "echogram"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "echogram"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val relSign = signingConfigs.findByName("release")
            signingConfig = if (relSign?.storeFile != null) relSign else signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    implementation(libs.tdlib)
}
