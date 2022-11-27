subprojects {
    group = "io.github.materiiapps"
    version = "1.0.0"

    repositories {
        mavenCentral()
        google()
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
