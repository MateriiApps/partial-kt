subprojects {
    group = "io.github.materiiapps"
    version = "1.1.0"

    repositories {
        mavenCentral()
        google()
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
