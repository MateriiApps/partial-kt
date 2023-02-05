package com.github.materiiapps.partial.example

import com.github.materiiapps.partial.Partial
import com.github.materiiapps.partial.Partialize
import kotlin.reflect.KClass

annotation class SampleAnnotation(
    val a: Int,
    val b: KClass<*>,
    val c: Array<KClass<*>>,
)

@Partialize(children = [
    AgedUser::class,
    UnknownUser::class
])
interface GenericUser {
    val name: String
}

@Partialize
@SampleAnnotation(0, AgedUser::class, [AgedUser::class])
data class AgedUser(
    @SampleAnnotation(0, AgedUser::class, [])
    override val name: String,

    val age: Int,
) : GenericUser

@Partialize
data class UnknownUser(
    override val name: String,
    val deleted: Boolean,
) : GenericUser

fun main() {
    val full = AgedUser(name = "Gregory", age = 14)
    val partial = AgedUserPartial(age = Partial.Value(15))
    val merged = full.merge(partial)
    println(merged)

    val generic: GenericUser = merged
    val newData: GenericUserPartial = AgedUserPartial(age = Partial.Value(15)) // must be of "same" type's partial
    val merged2 = generic.merge(newData)
    println(merged2)
}
