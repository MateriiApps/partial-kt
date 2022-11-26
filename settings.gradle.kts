pluginManagement {
    plugins {
        id("maven-publish") apply false
        kotlin("jvm") version "1.7.21" apply false
        kotlin("plugin.serialization") version "1.7.21" apply false
        id("com.google.devtools.ksp") version "1.7.21-1.0.8" apply false
        id("com.github.johnrengelman.shadow") version "7.1.2" apply false
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
