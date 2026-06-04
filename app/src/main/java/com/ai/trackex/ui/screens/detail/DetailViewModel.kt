package com.ai.trackex.ui.screens.detail

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository

    private val _expense = MutableStateFlow<Expense?>(null)
    val expense: StateFlow<Expense?> = _expense

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    val categories: StateFlow<List<String>>
    val categoryEmojiMap: StateFlow<Map<String, String>>

    init {
        val db = AppDatabase.getInstance(application)
        repository = ExpenseRepository(db.expenseDao())

        val categoryRepository = CategoryRepository(db.categoryDao())
        categories = categoryRepository.allCategories
            .map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        categoryEmojiMap = categoryRepository.allCategories
            .map { list -> list.associate { it.name to it.emoji } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    fun loadExpense(id: Long) {
        viewModelScope.launch {
            _expense.value = repository.getExpenseById(id)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
            _expense.value = expense
        }
    }

    fun deleteExpense() {
        viewModelScope.launch {
            _expense.value?.let {
                repository.deleteExpense(it)
                _deleted.value = true
            }
        }
    }
}
