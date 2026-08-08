package com.example.colorpaper.ui.home

data class WeekDayMood(
    val weekday: String,
    val month: Int,
    val dayOfMonth: Int,
    val dateKey: String,
    val emotionEmoji: String = "",
    val isToday: Boolean = false,
    val isFuture: Boolean = false
)
