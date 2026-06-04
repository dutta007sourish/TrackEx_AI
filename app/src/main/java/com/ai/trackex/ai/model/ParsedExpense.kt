package com.ai.trackex.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsedBillResponse(
    val items: List<ParsedExpenseItem>
)

@Serializable
data class ParsedExpenseItem(
    val date: String? = null,
    val time: String? = null,
    val amount: Double? = null,
    val category: String? = null,
    val note: String? = null
)
