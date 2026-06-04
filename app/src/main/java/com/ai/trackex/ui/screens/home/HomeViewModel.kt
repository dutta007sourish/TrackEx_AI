package com.ai.trackex.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.trackex.data.local.AppDatabase
import com.ai.trackex.data.local.Category
import com.ai.trackex.data.local.Expense
import com.ai.trackex.data.repository.CategoryRepository
import com.ai.trackex.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository

    val expenses: Flow<List<Expense>>

    val categoryEmojiMap: StateFlow<Map<String, String>>

    init {
        val db = AppDatabase.getInstance(application)
        repository = ExpenseRepository(db.expenseDao())
        expenses = repository.getAllExpenses()

        val categoryRepository = CategoryRepository(db.categoryDao())
        categoryEmojiMap = categoryRepository.allCategories
            .map { list -> list.associate { it.name to it.emoji } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    fun deleteExpenses(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteExpensesByIds(ids.toList())
        }
    }
}
