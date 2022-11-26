package com.github.materiapps.partial.example

import com.github.materiapps.partial.Partial
import com.github.materiapps.partial.Partialize

annotation class SampleAnnotation

@Partialize
@SampleAnnotation
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
