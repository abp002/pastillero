import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Lógica sin Android: qué suena y cuándo, qué se le dice al hijo y el cliente de ntfy.
// Se compila a Java 17 y solo usa APIs de java.time que Android tiene desde la API 26.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
