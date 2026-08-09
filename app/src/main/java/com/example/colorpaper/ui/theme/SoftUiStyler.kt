package com.example.colorpaper.ui.theme

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
        val palette = ThemeManager.currentPalette(view.context)
        val outline = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(view.context, palette.primaryText),
            38
        )
        val outlineWidth = maxOf(1, density.toInt())
        when (view) {
            is MaterialCardView -> {
                view.strokeWidth = if (view.id == R.id.card_quick_actions) 0 else outlineWidth
                view.strokeColor = outline
                view.cardElevation = 0f
                if (view.id != R.id.card_quick_actions) {
                    view.radius = maxOf(view.radius, 22f * density)
                }
            }
            is MaterialButton -> {
                view.strokeWidth = outlineWidth
                view.strokeColor = ColorStateList.valueOf(outline)
                view.cornerRadius = maxOf(view.cornerRadius, (20f * density).toInt())
                view.stateListAnimator = null
            }
            is Chip -> {
                view.chipStrokeWidth = density
                view.chipStrokeColor = ColorStateList.valueOf(outline)
            }
        }
        if (
            (view.isClickable || view.isLongClickable) &&
            view !is EditText &&
            view.id != R.id.card_quick_actions &&
            view.id != R.id.quick_action_reminder &&
            view.id != R.id.quick_action_flashcard
        ) {
            view.stateListAnimator = bouncyPressAnimator(view)
        }
    }

    /** Gives every tappable control the same soft squash-and-spring response. */
    private fun bouncyPressAnimator(view: View): StateListAnimator {
        fun scaleAnimator(target: Float, duration: Long, springBack: Boolean): AnimatorSet =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.SCALE_X, target),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, target)
                )
                this.duration = duration
                interpolator = if (springBack) {
                    OvershootInterpolator(2.2f)
                } else {
                    DecelerateInterpolator()
                }
            }

        return StateListAnimator().apply {
            addState(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                scaleAnimator(PRESSED_SCALE, PRESS_DURATION, false)
            )
            addState(
                intArrayOf(android.R.attr.state_enabled),
                scaleAnimator(1f, RELEASE_DURATION, true)
            )
            addState(intArrayOf(), scaleAnimator(1f, RELEASE_DURATION, false))
        }
    }

    private const val PRESSED_SCALE = 0.94f
    private const val PRESS_DURATION = 85L
    private const val RELEASE_DURATION = 280L
}
