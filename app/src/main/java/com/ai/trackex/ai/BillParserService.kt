package com.ai.trackex.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.ai.trackex.BuildConfig
import com.ai.trackex.ai.model.ChatCompletionRequest
import com.ai.trackex.ai.model.ChatCompletionResponse
import com.ai.trackex.ai.model.ContentPart
import com.ai.trackex.ai.model.ImageUrl
import com.ai.trackex.ai.model.Message
import com.ai.trackex.ai.model.MessageContent
import com.ai.trackex.ai.model.ParsedBillResponse
import com.ai.trackex.ai.model.ParsedExpenseItem
import com.ai.trackex.data.local.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class BillParserService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"
        private const val MAX_IMAGE_DIMENSION = 1024

        private const val SYSTEM_PROMPT = """You are an expense extraction assistant. You will receive a photo of a bill or receipt. Split the bill into individual line items and extract information for each item. Respond ONLY with a JSON object, no extra text, no markdown fences."""

        private fun buildUserPrompt(categories: List<Category>): String {
            val categoryInfo = categories.joinToString("\n") { cat ->
                if (cat.description.isNotBlank()) "- ${cat.name}: ${cat.description}" else "- ${cat.name}"
            }
            val categoryNames = categories.joinToString(", ") { it.name }
            return """Analyze this bill/receipt image. Split it into individual purchased items and extract each as a separate entry.

Return a JSON object with an "items" array. Each item should have:

{
  "items": [
    {
      "date": "YYYY-MM-DD",
      "time": "HH:mm",
      "amount": 123.45,
      "category": "one of: $categoryNames",
      "note": "exact item name and quantity from the bill"
    }
  ]
}

Available categories and what belongs in each:
$categoryInfo

Rules:
- Each line item from the bill should be a separate entry in the array
- The date and time should be the same for all items if the bill has a single date/time
- The amount should be the price of that individual item
- Category MUST be one of: $categoryNames. Use the descriptions above to decide the best match.
- Note should be the core item name and total quantity. Strip all brand names, adjectives, and modifiers. If the bill shows quantity × unit weight (e.g. "2 x 500g"), multiply them and write the final total (e.g. "1kg"). Always simplify units where possible (1000g → 1kg, 1000ml → 1L). For grocery/food items, express quantity in weight or volume (g, kg, ml, L) — never use "pcs" for groceries (e.g. "Sugar 1kg", "Cucumber 500g", "Milk 1L"). For non-grocery/countable items, use piece count (e.g. "Screwdriver 1pcs", "Light Bulb 2pcs"). Keep it short and generic.
- If a field cannot be determined, use null
- If the bill cannot be split into items, return a single item with the total amount

Respond ONLY with the JSON object."""
        }
    }

    suspend fun parseBillImage(imageUri: Uri, categories: List<Category> = emptyList()): Result<List<ParsedExpenseItem>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank()) {
                throw BillParserException(BillParserException.Kind.MISSING_API_KEY)
            }

            val base64Image = encodeImageToBase64(imageUri)

            val requestBody = ChatCompletionRequest(
                model = MODEL,
                messages = listOf(
                    Message(
                        role = "system",
                        content = MessageContent.Text(SYSTEM_PROMPT)
                    ),
                    Message(
                        role = "user",
                        content = MessageContent.Parts(
                            listOf(
                                ContentPart.TextPart(text = buildUserPrompt(categories)),
                                ContentPart.ImageUrlPart(
                                    imageUrl = ImageUrl(
                                        url = "data:image/jpeg;base64,$base64Image"
                                    )
                                )
                            )
                        )
                    )
                ),
                maxTokens = 2000
            )

            val requestJson = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw BillParserException(
                    kind = BillParserException.Kind.HTTP_ERROR,
                    httpCode = response.code,
                    message = errorBody
                )
            }

            val responseBody = response.body?.string()
                ?: throw BillParserException(BillParserException.Kind.EMPTY_RESPONSE)

            val chatResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            val content = chatResponse.choices.firstOrNull()?.message?.content
                ?: throw BillParserException(BillParserException.Kind.EMPTY_RESPONSE)

            val cleanedJson = content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = json.decodeFromString(ParsedBillResponse.serializer(), cleanedJson)
            Result.success(parsed.items)
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    suspend fun processVoiceEdit(
        currentItems: List<ParsedExpenseItem>,
        voiceTranscript: String,
        categories: List<Category> = emptyList()
    ): Result<List<ParsedExpenseItem>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank()) {
                throw BillParserException(BillParserException.Kind.MISSING_API_KEY)
            }

            val itemsJson = json.encodeToString(ParsedBillResponse.serializer(), ParsedBillResponse(currentItems))

            val categoryNames = categories.joinToString(", ") { it.name }
            val categoryInfo = categories.joinToString("\n") { cat ->
                if (cat.description.isNotBlank()) "- ${cat.name}: ${cat.description}" else "- ${cat.name}"
            }
            val systemPrompt = """You are an expense editing assistant. You will receive a list of expense items as JSON and a voice command from the user. Apply the requested edits to the items and return the updated JSON. Respond ONLY with the JSON object, no extra text, no markdown fences.

Valid categories:
$categoryInfo

Category must be one of: $categoryNames.
Date format: YYYY-MM-DD. Time format: HH:mm.
The user may ask to edit any field (date, time, amount, category, note) of any or all items. They may also ask to delete or add items.
Always return the full updated items list."""

            val userPrompt = """Current items:
$itemsJson

Voice command: "$voiceTranscript"

Apply the edits described in the voice command and return the updated JSON in the same format: {"items": [...]}"""

            val requestBody = ChatCompletionRequest(
                model = MODEL,
                messages = listOf(
                    Message(role = "system", content = MessageContent.Text(systemPrompt)),
                    Message(role = "user", content = MessageContent.Text(userPrompt))
                ),
                maxTokens = 2000
            )

            val requestJson = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw BillParserException(
                    kind = BillParserException.Kind.HTTP_ERROR,
                    httpCode = response.code,
                    message = errorBody
                )
            }

            val responseBody = response.body?.string()
                ?: throw BillParserException(BillParserException.Kind.EMPTY_RESPONSE)

            val chatResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            val content = chatResponse.choices.firstOrNull()?.message?.content
                ?: throw BillParserException(BillParserException.Kind.EMPTY_RESPONSE)

            val cleanedJson = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = json.decodeFromString(ParsedBillResponse.serializer(), cleanedJson)
            Result.success(parsed.items)
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    private fun mapError(e: Throwable): Throwable = when (e) {
        is BillParserException -> e
        is SerializationException -> BillParserException(BillParserException.Kind.DECODE_ERROR, cause = e)
        else -> e
    }

    private fun encodeImageToBase64(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image URI: $uri")

        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val scaledBitmap = scaleBitmap(originalBitmap, MAX_IMAGE_DIMENSION)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

        if (scaledBitmap !== originalBitmap) {
            scaledBitmap.recycle()
        }
        originalBitmap.recycle()

        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
