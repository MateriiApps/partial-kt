package com.github.materiiapps.partial.example

import com.github.materiiapps.partial.Partial
import com.github.materiiapps.partial.Partialize
import kotlin.reflect.KClass

annotation class SampleAnnotation(
    val a: Int,
    val b: KClass<*>,
)

@Partialize
@SampleAnnotation(0, User::class)
data class User(
    val name: String,

    @SampleAnnotation(0, User::class)
    val age: Int,
)

fun main() {
    val gregory14 = User(name = "Gregory", age = 14)
    val mariaNoAge = UserPartial(name = Partial.Value("maria"))
    val merged = mariaNoAge.merge(gregory14)
    println(merged)
}
