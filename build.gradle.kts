plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.21"
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.21-1.0.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}
