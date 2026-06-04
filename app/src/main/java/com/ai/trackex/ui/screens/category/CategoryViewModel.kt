package com.ai.trackex.ui.screens.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.trackex.data.local.AppDatabase
import com.ai.trackex.data.local.Category
import com.ai.trackex.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CategoryRepository

    val categories: StateFlow<List<Category>>

    init {
        val dao = AppDatabase.getInstance(application).categoryDao()
        repository = CategoryRepository(dao)
        categories = repository.allCategories
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch { repository.seedDefaults() }
    }

    fun addCategory(name: String, description: String = "", emoji: String = "📦") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insert(Category(name = name.trim(), description = description.trim(), emoji = emoji))
        }
    }

    fun updateCategory(category: Category, newName: String, newDescription: String = "", newEmoji: String = category.emoji) {
        if (newName.isBlank() || category.name == "Other") return
        viewModelScope.launch {
            repository.update(category.copy(name = newName.trim(), description = newDescription.trim(), emoji = newEmoji))
        }
    }

    fun deleteCategory(category: Category) {
        if (category.name == "Other") return
        viewModelScope.launch {
            repository.delete(category)
        }
    }
}
