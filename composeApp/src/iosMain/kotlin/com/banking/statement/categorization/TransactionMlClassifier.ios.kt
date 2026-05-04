package com.banking.statement.categorization

/**
 * iOS does not currently bundle an on-device ML model. Once a Core ML
 * model is shipped, replace the return value with the loading classifier
 * implementation.
 */
actual fun provideMlClassifier(): TransactionMlClassifier = NoOpTransactionMlClassifier
