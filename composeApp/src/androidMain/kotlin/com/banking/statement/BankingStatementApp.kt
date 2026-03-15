package com.banking.statement

import android.app.Application
import com.banking.statement.di.androidModule
import com.banking.statement.di.commonModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BankingStatementApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BankingStatementApp)
            modules(commonModule, androidModule)
        }
    }
}
