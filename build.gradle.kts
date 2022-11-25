plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.21"
}

repositories {
    mavenCentral()
    google()
}

group = "io.github.materiapps.partial"
version = "1.0.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.21-1.0.8")

    val poetVersion = "1.12.0"
    implementation("com.squareup:kotlinpoet:$poetVersion")
    implementation("com.squareup:kotlinpoet-ksp:$poetVersion")
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}
