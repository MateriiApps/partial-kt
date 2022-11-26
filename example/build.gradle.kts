plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    ksp(project(":partial-ksp"))
    implementation(project(":partial"))
}

kotlin {
    jvmToolchain(11)
    sourceSets {
        getByName("main") {
            kotlin.srcDir("build/generated/ksp/main/kotlin")
        }
    }
}