package com.banking.statement.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = BankingDatabase.Schema,
            context = context,
            name = "banking.db",
            callback = AndroidSqliteDriver.Callback(
                schema = BankingDatabase.Schema,
                *DatabaseMigration.getMigrationCallbacks()
            )
        )
    }
}
