package com.banking.statement

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform