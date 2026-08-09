package com.example.colorpaper.ui.theme

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.example.colorpaper.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout

/** Applies the selected app palette to profile and friend screens and list items. */
object ProfileThemeStyler {
    fun applyScreen(root: View) {
        val palette = ThemeManager.currentPalette(root.context)
        root.setBackgroundColor(color(root, palette.screenBackground))
        applyContent(root)
    }

    fun applyItem(root: View) {
        applyContent(root)
        SoftUiStyler.apply(root)
    }

    private fun applyContent(view: View) {
        val palette = ThemeManager.currentPalette(view.context)
        val text = color(view, palette.primaryText)
        val accent = color(view, palette.accent)
        val surface = color(view, palette.todo)
        val textOnAccent = color(view, palette.textOnAccent)
        val outline = ColorUtils.setAlphaComponent(text, 38)
        val secondaryText = ColorUtils.setAlphaComponent(text, 175)
        val density = view.resources.displayMetrics.density

        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(surface)
                view.strokeColor = outline
                view.strokeWidth = maxOf(1, density.toInt())
                view.cardElevation = 0f
            }
            is MaterialButton -> {
                view.backgroundTintList = ColorStateList.valueOf(accent)
                view.setTextColor(textOnAccent)
                view.strokeColor = ColorStateList.valueOf(outline)
                view.strokeWidth = maxOf(1, density.toInt())
            }
            is Button -> {
                view.backgroundTintList = ColorStateList.valueOf(accent)
                view.setTextColor(textOnAccent)
            }
            is EditText -> {
                view.setTextColor(text)
                view.setHintTextColor(ColorUtils.setAlphaComponent(text, 120))
                view.backgroundTintList = ColorStateList.valueOf(outline)
            }
            is TextView -> view.setTextColor(
                if (view.id in FILLED_TEXT_BUTTON_IDS) textOnAccent else text
            )
            is TextInputLayout -> {
                view.boxStrokeColor = accent
                view.hintTextColor = ColorStateList.valueOf(secondaryText)
            }
        }

        if (view.id in FILLED_TEXT_BUTTON_IDS) {
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(accent))
        }
        if (view is ImageView && view.id in THEMED_ICON_IDS) {
            view.imageTintList = ColorStateList.valueOf(text)
        }
        if (view.id in DIVIDER_IDS) view.setBackgroundColor(outline)

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyContent(view.getChildAt(index))
        }
    }

    private fun color(view: View, colorRes: Int): Int =
        ContextCompat.getColor(view.context, colorRes)

    private val FILLED_TEXT_BUTTON_IDS = setOf(R.id.btnFollowToggle, R.id.btnLogout)

    private val THEMED_ICON_IDS = setOf(
        R.id.ivBack,
        R.id.ivSearchFriend,
        R.id.ivEditProfile,
        R.id.ivClosePopup,
        R.id.ivSearch,
        R.id.btnFollow,
        R.id.btnBack,
        R.id.btnCalendar,
        R.id.btnProfileHome
    )

    private val DIVIDER_IDS = setOf(R.id.dividerHighlightsTop, R.id.dividerHighlightsBottom)
}
