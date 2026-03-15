package com.banking.statement.di

import org.koin.core.context.startKoin

fun initKoinIos() {
    startKoin {
        modules(commonModule, iosModule)
    }
}
