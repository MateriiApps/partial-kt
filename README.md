# partial-kt

A Kotlin KSP plugin for generating partial variants of classes.

## Installation

```kt
plugins {
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.materiiapps:partial:1.2.0")
    ksp("io.github.materiiapps:partial-ksp:1.2.0")
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

A single interface and direct implementations of it **are** supported, for a limited hierarchy.
Mark your interface with `@Partialize(children = [Implementation::class])`, noting that the implementation has to extend
the interface (obviously).

You can mark a property with (supported on interfaces too):

- `@Required` - To make the field not be boxed in a partial (required upon deserializing)
- `@Skip` - Omits the field from being generated in the partial class (requires default value

### Example

For a full example, please refer to the testing
file [here](./example/src/main/kotlin/com/github/materiiapps/partial/example/Data.kt).

Basic serializable class example:

```kt
@Partialize
@Serializable // Custom annotations are preserved
data class User(
    val name: String,
    val age: Int,

    @SerialName("parent_age") // Fields annotations are also preserved
    val parentAge: Int
)

fun main() {
    val gregory14 = User(name = "Gregory", age = 14, parentAge = 36)
    val mariaNoAge = UserPartial(name = Partial.Value("maria"))
    val merged = mariaNoAge.merge(gregory14)
    println(merged)
}
```
