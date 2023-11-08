subprojects {
    group = "io.github.materiiapps"
    version = "1.2.0"

    repositories {
        mavenCentral()
        google()
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
