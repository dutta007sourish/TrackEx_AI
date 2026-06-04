package com.ai.trackex.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int = 500
)

@Serializable
data class Message(
    val role: String,
    val content: MessageContent
)

@Serializable(with = MessageContentSerializer::class)
sealed class MessageContent {
    @Serializable
    data class Text(val text: String) : MessageContent()

    @Serializable
    data class Parts(val parts: List<ContentPart>) : MessageContent()
}

@Serializable
sealed class ContentPart {
    @Serializable
    @SerialName("text")
    data class TextPart(val text: String) : ContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImageUrlPart(
        @SerialName("image_url") val imageUrl: ImageUrl
    ) : ContentPart()
}

@Serializable
data class ImageUrl(val url: String)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ResponseMessage
)

@Serializable
data class ResponseMessage(
    val content: String? = null
)
