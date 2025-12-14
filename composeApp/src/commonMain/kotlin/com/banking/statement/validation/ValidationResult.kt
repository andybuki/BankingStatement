package com.banking.statement.validation

data class ValidationResult(
    val isValid: Boolean,
    val score: Int,
    val message: String,
    val details: List<String> = emptyList()
)
