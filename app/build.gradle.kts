import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The release key and its passwords live in the repository's local/ folder,
// which git ignores. A checkout without them still builds a release, unsigned.
val repoRoot: File = rootProject.projectDir
val keystore =
    Properties().apply {
        val file = repoRoot.resolve("local/keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

android {
    namespace = "io.github.vivekg7.galla"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vivekg7.galla"
        // Android 11 and newer: backups use SQLite's VACUUM INTO, which needs
        // SQLite 3.27 (docs/plans/001_offline_shop_app.md).
        minSdk = 30
        targetSdk = 37
        // Play refuses a versionCode it has seen: raise it for every upload.
        versionCode = 6
        versionName = "0.3.3"
        // Phones only: ML Kit's scanner is native code, and the x86 copies
        // (emulators, a few old tablets) would add 12 MB.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystore.isNotEmpty()) {
            create("release") {
                storeFile = repoRoot.resolve(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo { include = false }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // English only: the dependencies' translations would otherwise ship too.
    androidResources { localeFilters += listOf("en") }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ChromeOsAbiSupport: x86 is left out on purpose (defaultConfig above).
    lint { disable += "ChromeOsAbiSupport" }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin", "META-INF/*.version")
    }
}

room3 {
    // Exported schemas are what a migration is written and tested against.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.framework)
    ksp(libs.room.compiler)
    implementation(libs.camera.camera2)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)
    implementation(libs.coroutines.android)
    implementation(libs.play.services.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    constraints {
        // AndroidX lifecycle brings 1.7.3, and the test APK must use the app's
        // versions, but Room's MigrationTestHelper fails on anything below 1.8.1.
        implementation(libs.serialization.core)
    }
}
