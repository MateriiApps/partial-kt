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

task<Jar>("javadocJar") {
    from(tasks["javadoc"].outputs)
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        register(project.name, MavenPublication::class) {
            artifact(tasks["kotlinSourcesJar"])
            artifact(tasks["javadocJar"])
            artifact(tasks["shadowJar"]) {
                classifier = null
            }

            pom {
                name.set("partial-kt")
                description.set("A Kotlin KSP plugin for generating partial variants of classes.")
                url.set("https://github.com/MateriiApps/partial-kt")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
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
            signing {
                useInMemoryPgpKeys(
                    System.getenv("SIGNING_KEY_ID"),
                    System.getenv("SIGNING_KEY"),
                    System.getenv("SIGNING_PASSWORD"),
                )
                sign(publishing.publications)
            }

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
