pluginManagement {
    plugins {
        id("maven-publish") apply false
        kotlin("jvm") version "1.9.20" apply false
        kotlin("plugin.serialization") version "1.9.20" apply false
        id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
        id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "partial-kt"

include("partial")
include("partial-ksp")
include("example")
