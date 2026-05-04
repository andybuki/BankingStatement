package com.banking.statement.categorization

/**
 * Android does not currently bundle an on-device ML model. Once a TFLite
 * model is shipped under composeApp/src/androidMain/assets, replace the
 * return value with the loading classifier implementation.
 */
actual fun provideMlClassifier(): TransactionMlClassifier = NoOpTransactionMlClassifier
