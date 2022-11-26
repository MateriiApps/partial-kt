subprojects {
    group = "io.github.materiapps"
    version = "1.0.0"

    repositories {
        mavenCentral()
        google()
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
