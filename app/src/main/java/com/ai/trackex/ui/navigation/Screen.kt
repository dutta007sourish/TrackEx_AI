package com.ai.trackex.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddExpense : Screen("add_expense")
    data object Review : Screen("review/{imageUri}") {
        fun createRoute(imageUri: String): String = "review/$imageUri"
    }
    data object Detail : Screen("detail/{expenseId}") {
        fun createRoute(expenseId: Long): String = "detail/$expenseId"
    }
    data object ManualEntry : Screen("manual_entry")
    data object Categories : Screen("categories")
    data object Stats : Screen("stats")
}
