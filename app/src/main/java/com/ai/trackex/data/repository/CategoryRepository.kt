package com.ai.trackex.data.repository

import com.ai.trackex.data.local.Category
import com.ai.trackex.data.local.CategoryDao
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun getAllCategoriesList(): List<Category> = categoryDao.getAllCategoriesList()

    suspend fun insert(category: Category) = categoryDao.insert(category)

    suspend fun update(category: Category) = categoryDao.update(category)

    suspend fun delete(category: Category) = categoryDao.delete(category)

    suspend fun seedDefaults() {
        if (categoryDao.count() == 0) {
            val defaults = listOf(
                Category(name = "Food", description = "Groceries, restaurants, snacks, beverages, dining out", emoji = "🍔"),
                Category(name = "Transport", description = "Fuel, cab rides, bus/train tickets, tolls, parking", emoji = "🚗"),
                Category(name = "Shopping", description = "Clothing, electronics, household items, personal care products", emoji = "🛍️"),
                Category(name = "Entertainment", description = "Movies, games, subscriptions, events, hobbies", emoji = "🎬"),
                Category(name = "Utilities", description = "Electricity, water, gas, internet, phone bills, rent", emoji = "🏠"),
                Category(name = "Health", description = "Medicines, doctor visits, lab tests, gym, wellness", emoji = "💊"),
                Category(name = "Other", description = "Anything that does not fit the existing categories", emoji = "❗")
            )
            categoryDao.insertAll(defaults)
        }
    }
}
