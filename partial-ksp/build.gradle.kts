import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("signing")
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

signing {
    if (findProperty("signing.secretKeyRingFile") != null) {
        sign(publishing.publications)
    }
}

publishing {
    publications {
        register(project.name, MavenPublication::class) {
            artifact(tasks["kotlinSourcesJar"])
            artifact(tasks["shadowJar"]) {
                classifier = null
            }

            pom {
                name.set("partial-kt")
                description.set("A Kotlin KSP plugin for generating partial variants of classes.")
                url.set("https://github.com/MateriiApps/partial-kt")
                licenses {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
                developers {
                    developer {
                        id.set("rushii")
                        name.set("rushii")
                        url.set("https://github.com/DiamondMiner88/")
                        email.set("vdiamond_@outlook.com")
                    }
                    developer {
                        id.set("xinto")
                        name.set("Xinto")
                        url.set("https://github.com/X1nto/")
                    }
                }
                scm {
                    url.set("https://github.com/MateriiApps/partial-kt")
                    connection.set("scm:git:github.com/MateriiApps/partial-kt.git")
                    developerConnection.set("scm:git:ssh://github.com/MateriiApps/partial-kt.git")
                }
            }
        }
    }
    repositories {
        val sonatypeUsername = System.getenv("SONATYPE_USERNAME")
        val sonatypePassword = System.getenv("SONATYPE_PASSWORD")

        if (sonatypeUsername == null || sonatypePassword == null)
            mavenLocal()
        else {
            maven {
                credentials {
                    username = sonatypeUsername
                    password = sonatypePassword
                }
                setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
        }
    }
}
