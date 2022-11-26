plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.github.materiapps.partial"
version = "1.0.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}
