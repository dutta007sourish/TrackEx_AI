package com.ai.trackex.ai.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("MessageContent")

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is MessageContent.Text -> {
                jsonEncoder.encodeJsonElement(JsonPrimitive(value.text))
            }
            is MessageContent.Parts -> {
                val element = jsonEncoder.json.encodeToJsonElement(
                    ListSerializer(ContentPart.serializer()),
                    value.parts
                )
                jsonEncoder.encodeJsonElement(element)
            }
        }
    }

    override fun deserialize(decoder: Decoder): MessageContent {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> MessageContent.Text(element.content)
            is JsonArray -> {
                val parts = jsonDecoder.json.decodeFromJsonElement(
                    ListSerializer(ContentPart.serializer()),
                    element
                )
                MessageContent.Parts(parts)
            }
            else -> throw IllegalArgumentException("Unexpected MessageContent format")
        }
    }
}
