import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs +
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

publishing {
    publications {
        register(project.name, MavenPublication::class) {
            artifact(tasks["kotlinSourcesJar"])
            artifact(tasks["jar"]) {
                classifier = null
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
