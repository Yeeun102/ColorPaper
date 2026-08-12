package com.example.colorpaper.ui.calendar

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.ui.theme.ThemeManager
import com.example.colorpaper.ui.theme.ThemedDialogStyler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 기록이 존재하는 날짜를 점으로 표시하는 앱 공통 날짜 선택창. */
object RecordDatePickerDialog {
    fun show(
        context: Context,
        initialDate: String,
        recordedDates: Set<String>,
        onDateSelected: (String) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val monthFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
        val displayedMonth = Calendar.getInstance().apply {
            runCatching { keyFormat.parse(initialDate) }.getOrNull()?.let { time = it }
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val palette = ThemeManager.currentPalette(context)
        val textColor = ContextCompat.getColor(context, palette.primaryText)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        fun headerButton(label: String) = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 28f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val previous = headerButton("‹")
        val title = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        val next = headerButton("›")
        header.addView(previous); header.addView(title); header.addView(next)
        root.addView(header)

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                addView(TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
                })
            }
        })

        val calendar = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 7)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(432)
            )
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        }
        root.addView(calendar)

        lateinit var dialog: AlertDialog
        val adapter = MonthCalendarAdapter(palette) { day ->
            day.dateKey?.let {
                onDateSelected(it)
                dialog.dismiss()
            }
        }
        calendar.adapter = adapter

        fun renderMonth() {
            title.text = monthFormat.format(displayedMonth.time)
            val first = displayedMonth.clone() as Calendar
            first.set(Calendar.DAY_OF_MONTH, 1)
            val leading = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val lastDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = keyFormat.format(Date())
            val cells = MutableList(leading) { MonthDayUi(null, null) }
            for (dayNumber in 1..lastDay) {
                first.set(Calendar.DAY_OF_MONTH, dayNumber)
                val dateKey = keyFormat.format(first.time)
                cells += MonthDayUi(
                    dateKey = dateKey,
                    dayNumber = dayNumber,
                    isToday = dateKey == today,
                    hasRecord = dateKey in recordedDates
                )
            }
            while (cells.size < 42) cells += MonthDayUi(null, null)
            adapter.submitDays(cells, initialDate)
        }
        previous.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            renderMonth()
        }
        next.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            renderMonth()
        }

        dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.setOnShowListener { ThemedDialogStyler.apply(dialog, context) }
        renderMonth()
        dialog.show()
    }
}
