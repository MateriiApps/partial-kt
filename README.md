# partial-kt

A Kotlin KSP plugin for generating partial variants of classes.

## Installation

```kt
plugins {
    id("com.google.devtools.ksp") version "1.7.21-1.0.8"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.materiiapps:partial:1.0.0")
    ksp("io.github.materiiapps:partial-ksp:1.0.0")
}

kotlin {
    sourceSets {
        getByName("main") {
            kotlin.srcDir("build/generated/ksp/main/kotlin")
        }
    }
}
```

## Usage

Mark your target classes with `@Partialize`, then trigger a build to generate the partial classes for them to appear in
intellisense. The generated file will have the following:

- A class named `[name]Partial` extending `Partial<T=full type>`
- `Partial<T>#merge(T)` for merging generic partials with a full class
- `[name].toPartial()` for converting a full class to a partial
- `[name].merge(partial)` for merging a full class with a partial

```kt
@Partialize
@Serializable // custom annotations are preserved
data class User(
    val name: String,
    val age: Int,
)

fun main() {
    val gregory14 = User(name = "Gregory", age = 14)
    val mariaNoAge = UserPartial(name = Partial.Value("maria"))
    val merged = mariaNoAge.merge(gregory14)
    println(merged)
}
```
