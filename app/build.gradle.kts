plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
}

android {
    namespace = "es.abpdev.pastillero"
    compileSdk = 37

    defaultConfig {
        applicationId = "es.abpdev.pastillero"
        minSdk = 26
        // 36 a propósito: los cambios de comportamiento de Android 17 no están revisados.
        // Subirlo es una decisión, con pasada manual de alarmas y pantalla completa.
        //noinspection OldTargetApi
        targetSdk = 36
        // Android solo actualiza si el versionCode crece: sale de X.Y.Z (0.1.0 → 100, 1.2.3 → 10203).
        val version = providers.gradleProperty("pastilleroVersion").get()
        val (mayor, menor, parche) = version.split(".").map(String::toInt)
        versionName = version
        versionCode = mayor * 10_000 + menor * 100 + parche
    }

    // La clave de firma vive fuera del repo, en ~/.gradle/gradle.properties (PASTILLERO_*).
    // Sin ella el APK de release sale sin firmar y Android no lo instala.
    val almacen = providers.gradleProperty("PASTILLERO_KEYSTORE").orNull
    signingConfigs {
        if (almacen != null) {
            create("release") {
                storeFile = file(almacen)
                storePassword = providers.gradleProperty("PASTILLERO_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("PASTILLERO_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("PASTILLERO_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Lint limpio es parte de «hecho» (nivel produccion): un aviso nuevo pone `tipos` en rojo.
        warningsAsErrors = true
        abortOnError = true
        // «Hay versión nueva» no es un fallo del cambio: actualizar se decide aparte.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Robolectric toca las tripas de FileDescriptor (SQLite de Room); en JDK 17+ hay que abrírselas.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":nucleo"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
}
