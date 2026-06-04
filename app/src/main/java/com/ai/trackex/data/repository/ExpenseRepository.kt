package com.ai.trackex.data.repository

import com.ai.trackex.data.local.Expense
import com.ai.trackex.data.local.ExpenseDao
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAll()

    suspend fun getExpenseById(id: Long): Expense? = expenseDao.getById(id)

    suspend fun insertExpense(expense: Expense): Long = expenseDao.insert(expense)

    suspend fun insertAllExpenses(expenses: List<Expense>): List<Long> = expenseDao.insertAll(expenses)

    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)

    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    suspend fun deleteExpensesByIds(ids: List<Long>) = expenseDao.deleteByIds(ids)
}
