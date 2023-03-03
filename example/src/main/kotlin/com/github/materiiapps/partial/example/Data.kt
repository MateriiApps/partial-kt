package com.github.materiiapps.partial.example

import com.github.materiiapps.partial.Partial
import com.github.materiiapps.partial.Partialize
import com.github.materiiapps.partial.Required
import com.github.materiiapps.partial.Skip
import kotlin.reflect.KClass

annotation class SampleAnnotation(
    val a: Int = 0,
    val b: KClass<*>,
    val c: Array<KClass<*>>,
)

interface Thing {
    val w: Int
}

@Partialize(
    children = [
        AgedUser::class,
        UnknownUser::class
    ]
)
interface GenericUser {
    val name: String

    @Skip
    val private: Boolean
}

@Partialize
@SampleAnnotation(1, AgedUser::class, [AgedUser::class])
data class AgedUser(
    @SampleAnnotation(0, AgedUser::class, [])
    override val name: String,

    @SampleAnnotation(b = AgedUser::class, c = [])
    val age: Int,

    override val private: Boolean = false,  // overridden skipped property (needs default value),
    
    override val w: Int // should not put override on partial
) : GenericUser, Thing

@Partialize
data class UnknownUser(
    override val name: String,
    override val private: Boolean = true, // overridden skipped property (needs default value)

    @Required // marks this field to not be boxed (always present)
    val deleted: Boolean,

    @Skip
    val deleteDate: Long = 0, // new skipped field (needs default value)
) : GenericUser

fun main() {
    val full = AgedUser(name = "Gregory", age = 14, w = 76)
    val partial = AgedUserPartial(age = Partial.Value(15))
    val merged = full.merge(partial)
    println(merged)

    val generic: GenericUser = merged
    val newData: GenericUserPartial = AgedUserPartial(age = Partial.Value(15)) // must be of the same type's partial
    val merged2 = generic.merge(newData)
    println(merged2)
}
