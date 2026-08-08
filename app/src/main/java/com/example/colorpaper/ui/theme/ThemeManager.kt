package com.example.colorpaper.ui.theme

import android.content.Context
import com.example.colorpaper.R

/**
 * 앱 테마 선택과 팔레트를 한곳에서 관리
 * 설정 화면에서는 setTheme()만 호출하고 화면을 다시 그리면 됨
 */
object ThemeManager {
    private const val PREFERENCES_NAME = "colorpaper_theme"
    private const val KEY_THEME = "selected_theme"

    fun currentTheme(context: Context): AppTheme {
        val savedName = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, AppTheme.ROSE.name)
        return AppTheme.entries.firstOrNull { it.name == savedName } ?: AppTheme.ROSE
    }

    fun setTheme(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun currentPalette(context: Context): ThemePalette = palette(currentTheme(context))

    fun palette(theme: AppTheme): ThemePalette = when (theme) {
        AppTheme.ROSE -> ThemePalette(
            screenBackground = R.color.theme_rose_background,
            accent = R.color.theme_rose_accent,
            stroke = R.color.theme_rose_stroke,
            primaryText = R.color.theme_rose_text,
            textOnAccent = R.color.white,
            switchOff = R.color.theme_rose_switch_off,
            calendar = R.color.theme_rose_calendar,
            checklist = R.color.theme_rose_checklist,
            todo = R.color.theme_rose_todo,
            yearsAgo = R.color.theme_rose_years_ago,
            reminder = R.color.theme_rose_reminder
        )
        AppTheme.SAGE -> ThemePalette(
            screenBackground = R.color.theme_sage_background,
            accent = R.color.theme_sage_accent,
            stroke = R.color.theme_sage_stroke,
            primaryText = R.color.theme_sage_text,
            textOnAccent = R.color.white,
            switchOff = R.color.theme_sage_switch_off,
            calendar = R.color.theme_sage_calendar,
            checklist = R.color.theme_sage_checklist,
            todo = R.color.theme_sage_todo,
            yearsAgo = R.color.theme_sage_years_ago,
            reminder = R.color.theme_sage_reminder
        )
        AppTheme.SKY -> ThemePalette(
            screenBackground = R.color.theme_sky_background,
            accent = R.color.theme_sky_accent,
            stroke = R.color.theme_sky_stroke,
            primaryText = R.color.theme_sky_text,
            textOnAccent = R.color.white,
            switchOff = R.color.theme_sky_switch_off,
            calendar = R.color.theme_sky_calendar,
            checklist = R.color.theme_sky_checklist,
            todo = R.color.theme_sky_todo,
            yearsAgo = R.color.theme_sky_years_ago,
            reminder = R.color.theme_sky_reminder
        )
    }
}
