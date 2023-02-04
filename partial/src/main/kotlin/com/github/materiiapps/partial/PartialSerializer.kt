package com.github.materiiapps.partial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
