package com.example.colorpaper.ui.theme

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.colorpaper.R
import com.google.android.material.button.MaterialButton

/** 모든 AlertDialog가 같은 테마, 모서리, 간격과 버튼 규칙을 사용하도록 하는 공통 스타일러. */
object ThemedDialogStyler {
    fun apply(dialog: AlertDialog, context: Context) {
        val palette = ThemeManager.currentPalette(context)
        val backgroundColor = ContextCompat.getColor(context, palette.screenBackground)
        val text = ContextCompat.getColor(context, palette.primaryText)
        val reminder = ContextCompat.getColor(context, palette.reminder)
        val buttonColor = ColorUtils.blendARGB(backgroundColor, reminder, .66f)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        dialog.window?.setBackgroundDrawable(shape(backgroundColor, dp(28)))
        dialog.window?.decorView?.let { styleContent(it, text, buttonColor, density) }

        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { which ->
                dialog.getButton(which)?.apply {
                    setTextColor(text)
                    backgroundTintList = ColorStateList.valueOf(buttonColor)
                    background = shape(buttonColor, dp(19))
                    gravity = Gravity.CENTER
                    minWidth = dp(68)
                    minHeight = 0
                    setPadding(dp(14), 0, dp(14), 0)
                    (layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        params.height = dp(38)
                        params.setMargins(dp(5), 0, dp(5), 0)
                        layoutParams = params
                    }
                }
            }
        (dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.parent as? View)?.apply {
            setPadding(paddingLeft, dp(8), paddingRight, dp(16))
        }
    }

    /** 설정 템플릿 팝업처럼 넓고 여백 있는 규격을 사용하는 콘텐츠 팝업. */
    fun applyWidePopup(dialog: AlertDialog, context: Context) {
        apply(dialog, context)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        dialog.window?.apply {
            setLayout(context.resources.displayMetrics.widthPixels - dp(32), ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .58f }
        }
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach { which ->
            dialog.getButton(which)?.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    params.width = dp(88)
                    params.height = dp(40)
                    params.setMargins(dp(4), 0, dp(4), 0)
                    layoutParams = params
                }
            }
        }
        (dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.parent as? View)?.apply {
            setPadding(dp(20), dp(12), dp(20), dp(18))
        }
    }

    private fun styleContent(view: View, textColor: Int, buttonColor: Int, density: Float) {
        when (view) {
            is CompoundButton -> {
                view.background = null
                view.backgroundTintList = null
                view.buttonTintList = ColorStateList.valueOf(textColor)
                view.setTextColor(textColor)
            }
            is MaterialButton -> {
                view.cornerRadius = (19 * density).toInt()
                view.backgroundTintList = ColorStateList.valueOf(buttonColor)
                view.setTextColor(textColor)
                view.insetTop = 0
                view.insetBottom = 0
            }
            is Button -> {
                view.background = shape(buttonColor, (19 * density).toInt())
                view.setTextColor(textColor)
            }
            is EditText -> {
                view.setTextColor(textColor)
                view.background = GradientDrawable().apply {
                    cornerRadius = 16 * density
                    setColor(ColorUtils.setAlphaComponent(buttonColor, 58))
                    setStroke(maxOf(1, density.toInt()), ColorUtils.setAlphaComponent(textColor, 45))
                }
                view.setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            }
            is TextView -> view.setTextColor(textColor)
        }
        if (view.id == R.id.tv_reminder_record) {
            view.background = shape(ColorUtils.setAlphaComponent(buttonColor, 150), (16 * density).toInt())
        }
        if (view is ViewGroup) repeat(view.childCount) {
            styleContent(view.getChildAt(it), textColor, buttonColor, density)
        }
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }
}
