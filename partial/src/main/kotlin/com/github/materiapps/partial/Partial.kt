@file:JvmName("PartialableKt")
@file:Suppress("NOTHING_TO_INLINE")

package com.github.materiapps.partial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a value that can be present or missing.
 * This is serializable.
 */
@Serializable(PartialSerializer::class)
public sealed interface Partial<out T> {
    public class Value<out T>(public val value: T) : Partial<T> {
        override fun toString(): String = value.toString()

        override fun equals(other: Any?): Boolean {
            val value = other as? Value<*> ?: return false
            return value.value == this.value
        }

        override fun hashCode(): Int = value.hashCode()
    }

    public object Missing : Partial<Nothing> {
        override fun toString(): String = "<Missing>"

        override fun equals(other: Any?): Boolean = other is Missing

        override fun hashCode(): Int = 0
    }
}

internal class PartialSerializer<T>(
    private val valueSerializer: KSerializer<T>,
) : KSerializer<Partial<T?>> {
    override val descriptor: SerialDescriptor
        get() = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Partial<T?>) {
        if (value is Partial.Value) {
            if (value.value != null) {
                encoder.encodeNotNullMark()
                encoder.encodeSerializableValue(valueSerializer, value.value)
            } else {
                encoder.encodeNull()
            }
        }
    }

    override fun deserialize(decoder: Decoder): Partial<T?> {
        val value = if (!decoder.decodeNotNullMark()) {
            decoder.decodeNull()
        } else {
            decoder.decodeSerializableValue(valueSerializer)
        }

        return Partial.Value(value)
    }
}

public inline fun <T> Partial<T>.getOrElse(crossinline block: () -> T): T {
    return when (this) {
        is Partial.Missing -> block()
        is Partial.Value -> value
    }
}

public inline fun <T> Partial<T>.getOrNull(): T? = getOrElse { null }

public inline fun <T, R> Partial<T>.map(block: (T) -> R): Partial<R> {
    return when (this) {
        is Partial.Missing -> this
        is Partial.Value -> Partial.Value(block(value))
    }
}
