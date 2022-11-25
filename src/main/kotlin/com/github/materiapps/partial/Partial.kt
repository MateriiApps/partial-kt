@file:OptIn(ExperimentalSerializationApi::class)

package com.github.materiapps.partial

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(Partial.Serializer::class)
public sealed class Partial<out T> {
    public class Value<out T>(public val value: T) : Partial<T>() {
        override fun toString(): String = value.toString()

        override fun equals(other: Any?): Boolean {
            val value = other as? Value<*> ?: return false
            return value.value == this.value
        }

        override fun hashCode(): Int = value.hashCode()
    }

    public object Missing : Partial<Nothing>() {
        override fun toString(): String = "Missing"

        override fun equals(other: Any?): Boolean = other is Missing

        override fun hashCode(): Int = 0
    }

    internal class Serializer<T>(
        private val valueSerializer: KSerializer<T>
    ) : KSerializer<Partial<T?>> {
        override val descriptor: SerialDescriptor
            get() = valueSerializer.descriptor

        override fun serialize(encoder: Encoder, value: Partial<T?>) {
            if (value is Value) {
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

            return Value(value)
        }
    }
}

public inline fun <T> Partial<T>.getOrElse(block: () -> T): T {
    return when (this) {
        is Partial.Missing -> block()
        is Partial.Value -> value
    }
}

public fun <T> Partial<T>.getOrNull(): T? = getOrElse { null }

public inline fun <T, R> Partial<T>.mapToPartial(block: (T) -> R): Partial<R> {
    return when (this) {
        is Partial.Missing -> this
        is Partial.Value -> Partial.Value(block(value))
    }
}
