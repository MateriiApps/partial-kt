@file:JvmName("PartialableKt")
@file:Suppress("NOTHING_TO_INLINE")

package com.github.materiiapps.partial

import kotlinx.serialization.Serializable

/**
 * Represents a serializable ([PartialSerializer]) value that can be present or missing.
 */
@Serializable(PartialSerializer::class)
public sealed interface Partial<out T> {
    /**
     * Represents a value that exists (including null)
     */
    public class Value<out T>(public val value: T) : Partial<T> {
        override fun toString(): String = value.toString()

        override fun equals(other: Any?): Boolean {
            val value = other as? Value<*> ?: return false
            return value.value == this.value
        }

        override fun hashCode(): Int = value.hashCode()
    }

    /**
     * A missing value that does not get serialized.
     * When deserializing, if the value is missing then it is represented with this.
     */
    public object Missing : Partial<Nothing> {
        override fun toString(): String = "<Missing>"

        override fun equals(other: Any?): Boolean = other is Missing

        override fun hashCode(): Int = 0
    }
}

/**
 * Shortcut utility function for the [Partial.Value] class
 */
public inline fun <T> partial(value: T): Partial.Value<T> = Partial.Value(value)

/**
 * Shortcut utility function for the [Partial.Missing] singleton
 */
public inline fun missing(): Partial.Missing = Partial.Missing

/**
 * Gets the present value or generate a value with [block] if it's missing.
 */
public inline fun <T> Partial<T>.getOrElse(crossinline block: () -> T): T {
    return when (this) {
        is Partial.Missing -> block()
        is Partial.Value -> value
    }
}

/**
 * Gets the present value or null if missing.
 * **NOTE**: There is no way to differentiate between a present null value and missing with this function.
 */
public inline fun <T> Partial<T>.getOrNull(): T? = getOrElse { null }

/**
 * Transform a present value into another, doing nothing if missing.
 */
public inline fun <T, R> Partial<T>.map(block: (T) -> R): Partial<R> {
    return when (this) {
        is Partial.Missing -> this
        is Partial.Value -> Partial.Value(block(value))
    }
}
