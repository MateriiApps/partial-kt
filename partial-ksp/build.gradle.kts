plugins {
    kotlin("jvm")
}

group = "io.github.materiapps.partial.ksp"
version = "1.0.0"

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.21-1.0.8")

    val poetVersion = "1.12.0"
    implementation("com.squareup:kotlinpoet:$poetVersion")
    implementation("com.squareup:kotlinpoet-ksp:$poetVersion")

    api(project(":partial"))
}