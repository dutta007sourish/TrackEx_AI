package com.ai.trackex.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.trackex.data.local.AppDatabase
import com.ai.trackex.data.local.Expense
import com.ai.trackex.data.repository.CategoryRepository
import com.ai.trackex.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class StatsMode { MONTHLY, ANNUAL }

data class CategoryStat(
    val category: String,
    val emoji: String,
    val amount: Double,
    val percentage: Float
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    private val categoryRepository: CategoryRepository

    val categoryEmojiMap: StateFlow<Map<String, String>>

    private val _mode = MutableStateFlow(StatsMode.MONTHLY)
    val mode: StateFlow<StatsMode> = _mode

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth

    val expenses: StateFlow<List<Expense>>

    val stats: StateFlow<List<CategoryStat>>

    init {
        val db = AppDatabase.getInstance(application)
        repository = ExpenseRepository(db.expenseDao())
        categoryRepository = CategoryRepository(db.categoryDao())

        categoryEmojiMap = categoryRepository.allCategories
            .map { list -> list.associate { it.name to it.emoji } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        expenses = repository.getAllExpenses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        stats = combine(expenses, _mode, _selectedYear, _selectedMonth, categoryEmojiMap) { allExpenses, currentMode, year, month, emojiMap ->
            val filtered = allExpenses.filter { expense ->
                val cal = Calendar.getInstance().apply { timeInMillis = expense.date }
                val expYear = cal.get(Calendar.YEAR)
                val expMonth = cal.get(Calendar.MONTH)
                when (currentMode) {
                    StatsMode.MONTHLY -> expYear == year && expMonth == month
                    StatsMode.ANNUAL -> expYear == year
                }
            }

            val total = filtered.sumOf { it.amount }
            if (total == 0.0) return@combine emptyList()

            filtered
                .groupBy { it.category }
                .map { (category, items) ->
                    val sum = items.sumOf { it.amount }
                    CategoryStat(
                        category = category,
                        emoji = emojiMap[category] ?: "📦",
                        amount = sum,
                        percentage = (sum / total * 100).toFloat()
                    )
                }
                .sortedByDescending { it.amount }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setMode(mode: StatsMode) {
        _mode.value = mode
    }

    fun previousPeriod() {
        if (_mode.value == StatsMode.MONTHLY) {
            if (_selectedMonth.value == 0) {
                _selectedMonth.value = 11
                _selectedYear.value -= 1
            } else {
                _selectedMonth.value -= 1
            }
        } else {
            _selectedYear.value -= 1
        }
    }

    fun nextPeriod() {
        if (_mode.value == StatsMode.MONTHLY) {
            if (_selectedMonth.value == 11) {
                _selectedMonth.value = 0
                _selectedYear.value += 1
            } else {
                _selectedMonth.value += 1
            }
        } else {
            _selectedYear.value += 1
        }
    }
}
