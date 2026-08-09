package com.example.colorpaper.ui.theme

import android.view.View
import android.view.ViewGroup
import com.example.colorpaper.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/** Applies the shared borderless, rounded visual language to fragment views. */
object SoftUiStyler {
    fun apply(root: View) {
        style(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                apply(root.getChildAt(index))
            }
        }
    }

    private fun style(view: View) {
        val density = view.resources.displayMetrics.density
        when (view) {
            is MaterialCardView -> {
                view.strokeWidth = 0
                view.cardElevation = 0f
                if (view.id != R.id.card_quick_actions) {
                    view.radius = maxOf(view.radius, 22f * density)
                }
            }
            is MaterialButton -> {
                view.strokeWidth = 0
                view.cornerRadius = maxOf(view.cornerRadius, (20f * density).toInt())
                view.stateListAnimator = null
            }
            is Chip -> {
                view.chipStrokeWidth = 0f
            }
        }
    }
}
