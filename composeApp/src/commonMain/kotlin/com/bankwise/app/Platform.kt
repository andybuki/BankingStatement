package com.bankwise.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform