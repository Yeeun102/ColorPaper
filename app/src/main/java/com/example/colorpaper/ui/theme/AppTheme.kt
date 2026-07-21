package com.example.colorpaper.ui.theme

import androidx.annotation.ColorRes

enum class AppTheme {
    ROSE,
    SAGE,
    SKY
}

data class ThemePalette(
    @ColorRes val screenBackground: Int,
    @ColorRes val accent: Int,
    @ColorRes val stroke: Int,
    @ColorRes val primaryText: Int,
    @ColorRes val textOnAccent: Int,
    @ColorRes val switchOff: Int,
    @ColorRes val calendar: Int,
    @ColorRes val checklist: Int,
    @ColorRes val todo: Int,
    @ColorRes val yearsAgo: Int,
    @ColorRes val reminder: Int
)
