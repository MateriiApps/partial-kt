package com.github.materiapps.partial.example

import com.github.materiapps.partial.PartialValue
import com.github.materiapps.partial.Partialize

annotation class ExampleThirdPartyAnnotation

@Partialize
@ExampleThirdPartyAnnotation
data class User(
    val name: String,
    val age: Int,
)

fun main() {
    val gregory14 = User(name = "Gregory", age = 14)
    val mariaNoAge = UserPartial(name = PartialValue.Value("maria"))
    val merged = mariaNoAge.merge(gregory14)
    println(merged)
}