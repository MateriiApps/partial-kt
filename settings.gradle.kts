pluginManagement {
    plugins {
        id("com.google.devtools.ksp") version "1.7.21-1.0.8" apply false
        kotlin("jvm") version "1.7.21" apply false
        kotlin("plugin.serialization") version "1.7.21" apply false
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "partial-kt"

include("partial")
include("partial-ksp")
include("example")