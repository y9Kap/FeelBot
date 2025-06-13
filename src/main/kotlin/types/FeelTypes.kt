package org.example.types

enum class RangeResolution {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

data class FeelNote(
    val timestamp: Long,
    val feel: Int
)
