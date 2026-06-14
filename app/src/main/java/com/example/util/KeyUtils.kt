package com.example.util

fun String.isRealKey(): Boolean {
    val trimmed = this.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.startsWith("MY_", ignoreCase = true)) return false
    if (trimmed.contains("placeholder", ignoreCase = true)) return false
    if (trimmed.contains("insert_your", ignoreCase = true)) return false
    if (trimmed.contains("your_api", ignoreCase = true)) return false
    return true
}

fun String.getRealOrEmpty(): String {
    return if (this.isRealKey()) this.trim() else ""
}
