package com.ai.trackex.ui.screens.review

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.trackex.ai.BillParserService
import com.ai.trackex.ai.model.ParsedExpenseItem
import com.ai.trackex.data.local.AppDatabase
import com.ai.trackex.data.local.Category
import com.ai.trackex.data.local.Expense
import com.ai.trackex.data.repository.CategoryRepository
import com.ai.trackex.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ai.trackex.util.TempImageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ExpenseItemState(
    val date: Long = System.currentTimeMillis(),
    val amount: String = "",
    val category: String = "Other",
    val note: String = ""
)

data class ReviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<ExpenseItemState> = emptyList(),
    val saved: Boolean = false,
    val isListening: Boolean = false,
    val isProcessingVoice: Boolean = false,
    val voiceError: String? = null
)

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    private val categoryRepository: CategoryRepository
    private val billParser: BillParserService
    private var imageUri: String = ""

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    val categories: StateFlow<List<String>>
    val categoryEmojiMap: StateFlow<Map<String, String>>
    private val categoryObjects: StateFlow<List<Category>>

    init {
        val db = AppDatabase.getInstance(application)
        repository = ExpenseRepository(db.expenseDao())
        categoryRepository = CategoryRepository(db.categoryDao())
        billParser = BillParserService(application)

        categoryObjects = categoryRepository.allCategories
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        categories = categoryRepository.allCategories
            .map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        categoryEmojiMap = categoryRepository.allCategories
            .map { list -> list.associate { it.name to it.emoji } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        viewModelScope.launch { categoryRepository.seedDefaults() }
    }

    fun startManualEntry() {
        if (imageUri == "manual") return
        imageUri = "manual"
        _uiState.value = ReviewUiState(
            isLoading = false,
            items = listOf(ExpenseItemState())
        )
    }

    fun parseImage(uri: String) {
        if (imageUri == uri) return
        imageUri = uri

        viewModelScope.launch {
            _uiState.value = ReviewUiState(isLoading = true)

            // Wait for categories to load from database before parsing
            val cats = categoryRepository.allCategories.first { it.isNotEmpty() }
            val result = billParser.parseBillImage(Uri.parse(uri), cats)

            val validCategoryNames = cats.map { it.name }.toSet()
            result.fold(
                onSuccess = { parsedItems ->
                    val items = parsedItems.map { it.toItemState(validCategoryNames) }
                    _uiState.value = ReviewUiState(
                        isLoading = false,
                        items = items
                    )
                },
                onFailure = { error ->
                    _uiState.value = ReviewUiState(
                        isLoading = false,
                        error = toUserFriendlyError(error),
                        items = listOf(ExpenseItemState())
                    )
                }
            )
        }
    }

    fun updateItemDate(index: Int, value: Long) {
        updateItem(index) { it.copy(date = value) }
    }

    fun updateItemAmount(index: Int, value: String) {
        updateItem(index) { it.copy(amount = value) }
    }

    fun updateItemCategory(index: Int, value: String) {
        updateItem(index) { it.copy(category = value) }
    }

    fun updateItemNote(index: Int, value: String) {
        updateItem(index) { it.copy(note = value) }
    }

    fun addCategory(name: String, description: String, emoji: String = "📦") {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.insert(Category(name = name.trim(), description = description.trim(), emoji = emoji))
        }
    }

    fun addItem() {
        val current = _uiState.value
        val updated = current.items + ExpenseItemState()
        _uiState.value = current.copy(items = updated)
    }

    fun removeItem(index: Int) {
        val current = _uiState.value
        val updated = current.items.toMutableList().apply { removeAt(index) }
        _uiState.value = current.copy(items = updated)
    }

    private fun updateItem(index: Int, transform: (ExpenseItemState) -> ExpenseItemState) {
        val current = _uiState.value
        val updated = current.items.toMutableList()
        if (index in updated.indices) {
            updated[index] = transform(updated[index])
            _uiState.value = current.copy(items = updated)
        }
    }

    fun startListening() {
        val app = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            _uiState.value = _uiState.value.copy(voiceError = "Speech recognition not available on this device")
            return
        }

        _uiState.value = _uiState.value.copy(isListening = true, voiceError = null)

        val recognizer = SpeechRecognizer.createSpeechRecognizer(app)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull() ?: ""
                recognizer.destroy()
                _uiState.value = _uiState.value.copy(isListening = false)
                if (transcript.isNotBlank()) {
                    processVoiceCommand(transcript)
                }
            }

            override fun onError(error: Int) {
                recognizer.destroy()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Speech recognition failed (error $error)"
                }
                _uiState.value = _uiState.value.copy(isListening = false, voiceError = msg)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    fun clearVoiceError() {
        _uiState.value = _uiState.value.copy(voiceError = null)
    }

    private fun processVoiceCommand(transcript: String) {
        val state = _uiState.value
        _uiState.value = state.copy(isProcessingVoice = true, voiceError = null)

        val currentParsedItems = state.items.map { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.date }
            val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            ParsedExpenseItem(
                date = dateSdf.format(cal.time),
                time = timeSdf.format(cal.time),
                amount = item.amount.toDoubleOrNull(),
                category = item.category,
                note = item.note
            )
        }

        viewModelScope.launch {
            val cats = categoryRepository.allCategories.first { it.isNotEmpty() }
            val validCategoryNames = cats.map { it.name }.toSet()
            val result = billParser.processVoiceEdit(currentParsedItems, transcript, cats)
            result.fold(
                onSuccess = { updatedItems ->
                    val newItems = updatedItems.map { it.toItemState(validCategoryNames) }
                    _uiState.value = _uiState.value.copy(
                        items = newItems,
                        isProcessingVoice = false
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isProcessingVoice = false,
                        voiceError = "AI was unable to process your request"
                    )
                }
            )
        }
    }

    fun confirmExpenses() {
        val state = _uiState.value
        val expenses = state.items.mapNotNull { item ->
            val amount = item.amount.toDoubleOrNull() ?: return@mapNotNull null
            Expense(
                amount = amount,
                category = item.category,
                date = item.date,
                note = item.note.ifBlank { "—" }
            )
        }

        viewModelScope.launch {
            repository.insertAllExpenses(expenses)
            cleanupTempFile()
            _uiState.value = state.copy(saved = true)
        }
    }

    fun cancel() {
        cleanupTempFile()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupTempFile()
    }

    private fun toUserFriendlyError(error: Throwable): String {
        val message = error.message ?: ""
        return when {
            error is java.net.UnknownHostException ||
                message.contains("Unable to resolve host", ignoreCase = true) ->
                "No internet connection. Please check your network and try again."

            error is javax.net.ssl.SSLException ||
                message.contains("CertPathValidator", ignoreCase = true) ||
                message.contains("SSL", ignoreCase = true) ||
                message.contains("Trust anchor", ignoreCase = true) ->
                "Secure connection failed. Please check your network or try again later."

            error is java.net.SocketTimeoutException ||
                message.contains("timeout", ignoreCase = true) ->
                "Request timed out. Please try again."

            message.contains("API key", ignoreCase = true) ||
                message.contains("401", ignoreCase = true) ||
                message.contains("Unauthorized", ignoreCase = true) ->
                "Invalid API key. Please check your OpenAI API key in settings."

            message.contains("429", ignoreCase = true) ||
                message.contains("rate limit", ignoreCase = true) ->
                "Too many requests. Please wait a moment and try again."

            message.contains("500", ignoreCase = true) ||
                message.contains("server error", ignoreCase = true) ->
                "The AI service is temporarily unavailable. Please try again later."

            message.contains("parse", ignoreCase = true) ||
                message.contains("JSON", ignoreCase = true) ->
                "Could not read the bill. Try taking a clearer photo."

            else -> "Something went wrong while analyzing the bill. Please try again."
        }
    }

    private fun cleanupTempFile() {
        if (imageUri.isNotEmpty()) {
            TempImageManager.deleteTempFile(getApplication(), imageUri)
        }
    }

    private fun ParsedExpenseItem.toItemState(validCategories: Set<String> = emptySet()): ExpenseItemState {
        val dateTimeMillis = parseDateTime(date, time)
        val validCategory = if (validCategories.isNotEmpty() && category != null && category !in validCategories) {
            "Other"
        } else {
            category ?: "Other"
        }
        return ExpenseItemState(
            date = dateTimeMillis,
            amount = amount?.toString() ?: "",
            category = validCategory,
            note = note ?: ""
        )
    }

    private fun parseDateTime(dateStr: String?, timeStr: String?): Long {
        if (dateStr == null) return System.currentTimeMillis()
        return try {
            val cal = Calendar.getInstance()
            val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = dateSdf.parse(dateStr) ?: return System.currentTimeMillis()
            cal.time = parsedDate

            if (timeStr != null) {
                try {
                    val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val parsedTime = timeSdf.parse(timeStr)
                    if (parsedTime != null) {
                        val timeCal = Calendar.getInstance().apply { time = parsedTime }
                        cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                        cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    }
                } catch (_: Exception) { }
            }

            cal.timeInMillis
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

}
