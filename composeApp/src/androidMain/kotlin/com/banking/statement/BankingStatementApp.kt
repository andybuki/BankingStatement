package com.banking.statement

import android.app.Application
import com.banking.statement.di.androidModule
import com.banking.statement.di.commonModule
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BankingStatementApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize SQLCipher native library
        System.loadLibrary("sqlcipher")

        startKoin {
            androidContext(this@BankingStatementApp)
            modules(commonModule, androidModule)
        }
    }
}
