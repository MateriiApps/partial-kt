import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    api(kotlin("stdlib"))
    api(project(":partial"))
    api("com.google.devtools.ksp:symbol-processing-api:1.7.21-1.0.8")

    val poetVersion = "1.12.0"
    implementation("com.squareup:kotlinpoet:$poetVersion")
    implementation("com.squareup:kotlinpoet-ksp:$poetVersion")
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<ShadowJar> {
    dependencies {
        exclude { !it.moduleName.startsWith("kotlinpoet") }
    }
}

publishing {
    publications {
        register(project.name, MavenPublication::class) {
            artifact(tasks["kotlinSourcesJar"])
            artifact(tasks["shadowJar"]) {
                classifier = null
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
