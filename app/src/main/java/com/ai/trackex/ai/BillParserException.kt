package com.ai.trackex.ai

/**
 * Typed exception for bill-parsing failures so the UI layer can map errors to
 * user-friendly messages reliably (instead of string-matching on raw messages).
 */
class BillParserException(
    val kind: Kind,
    val httpCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Kind {
        MISSING_API_KEY,
        HTTP_ERROR,
        EMPTY_RESPONSE,
        DECODE_ERROR
    }
}
