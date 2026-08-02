package com.example.colorpaper.ui.home

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.model.WidgetEntity
import com.example.colorpaper.ui.theme.ThemePalette
import com.google.android.material.card.MaterialCardView
import java.util.Collections

class HomeWidgetAdapter(
    widgets: List<WidgetEntity>,
    private val palette: ThemePalette,
    private val onCalendarDateClick: (String) -> Unit = {}
) : RecyclerView.Adapter<HomeWidgetAdapter.WidgetViewHolder>() {

    private val widgetList = widgets.toMutableList()
    private var editMode = false
    private val todoChecked = mutableListOf(false, false)
    private var calendarDays: List<WeekDayMood> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_widget, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        holder.bind(widgetList[position])
    }

    override fun getItemCount(): Int = widgetList.size

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        notifyDataSetChanged()
    }

    fun updateData(newData: List<WidgetEntity>) {
        widgetList.clear()
        widgetList.addAll(newData.sortedBy { it.order })
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (!editMode || from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
            return false
        }
        Collections.swap(widgetList, from, to)
        notifyItemMoved(from, to)
        return true
    }

    fun currentWidgets(): List<WidgetEntity> = widgetList.toList()

    fun updateCalendarDays(days: List<WeekDayMood>) {
        calendarDays = days
        val position = widgetList.indexOfFirst { it.type == "CALENDAR" }
        if (position >= 0) notifyItemChanged(position)
    }

    inner class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view as MaterialCardView
        private val title: TextView = view.findViewById(R.id.tv_widget_title)
        private val content: TextView = view.findViewById(R.id.tv_widget_content)
        private val visibilitySwitch: SwitchCompat = view.findViewById(R.id.switch_widget_visibility)
        private val dragHandle: TextView = view.findViewById(R.id.tv_drag_handle)
        private val todoLayout: LinearLayout = view.findViewById(R.id.layout_todo_items)
        private val weeklyCalendar: LinearLayout = view.findViewById(R.id.layout_week_calendar)
        private val todoOne: CheckBox = view.findViewById(R.id.cb_home_todo_1)
        private val todoTwo: CheckBox = view.findViewById(R.id.cb_home_todo_2)

        fun bind(widget: WidgetEntity) {
            val display = widgetDisplay(widget.type)
            val textColor = color(palette.primaryText)
            val accentColor = color(palette.accent)
            val offColor = color(palette.switchOff)

            card.setCardBackgroundColor(color(widgetColor(widget.type)))
            card.strokeColor = color(palette.stroke)
            title.text = display.first
            content.text = display.second
            title.setTextColor(textColor)
            content.setTextColor(textColor)
            todoOne.setTextColor(textColor)
            todoTwo.setTextColor(textColor)
            dragHandle.setTextColor(textColor)

            visibilitySwitch.thumbTintList = ColorStateList.valueOf(color(palette.textOnAccent))
            visibilitySwitch.trackTintList = stateColors(accentColor, offColor)
            todoOne.buttonTintList = stateColors(accentColor, color(palette.stroke))
            todoTwo.buttonTintList = stateColors(accentColor, color(palette.stroke))

            val isTodo = widget.type == "TODO_LIST"
            val isCalendar = widget.type == "CALENDAR"
            content.visibility = if (editMode || isTodo || isCalendar) View.GONE else View.VISIBLE
            weeklyCalendar.visibility = if (!editMode && isCalendar) View.VISIBLE else View.GONE
            if (isCalendar && !editMode) bindWeeklyCalendar(textColor)
            todoLayout.visibility = if (!editMode && isTodo) View.VISIBLE else View.GONE
            visibilitySwitch.visibility = if (editMode) View.VISIBLE else View.GONE
            dragHandle.visibility = if (editMode) View.VISIBLE else View.GONE
            card.setOnClickListener(null)
            card.isClickable = false

            todoOne.setOnCheckedChangeListener(null)
            todoTwo.setOnCheckedChangeListener(null)
            todoOne.isChecked = todoChecked[0]
            todoTwo.isChecked = todoChecked[1]
            todoOne.setOnCheckedChangeListener { _, checked -> todoChecked[0] = checked }
            todoTwo.setOnCheckedChangeListener { _, checked -> todoChecked[1] = checked }

            visibilitySwitch.setOnCheckedChangeListener(null)
            visibilitySwitch.isChecked = widget.isVisible
            visibilitySwitch.setOnCheckedChangeListener { _, checked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    widgetList[position].isVisible = checked
                }
            }
        }

        private fun color(@ColorRes colorRes: Int): Int =
            ContextCompat.getColor(itemView.context, colorRes)

        private fun bindWeeklyCalendar(textColor: Int) {
            weeklyCalendar.removeAllViews()
            weeklyCalendar.clipToOutline = true
            weeklyCalendar.background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(color(palette.reminder))
                setStroke(dp(1), color(palette.stroke))
            }
            calendarDays.forEachIndexed { index, day ->
                weeklyCalendar.addView(createDayCell(day, textColor))
                if (index < calendarDays.lastIndex) {
                    weeklyCalendar.addView(verticalDivider())
                }
            }
        }

        private fun createDayCell(day: WeekDayMood, textColor: Int): View {
            val context = itemView.context
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(82), 1f)
                addView(
                    calendarCellText(
                        if (day.isToday) "today" else "${day.month}/${day.dayOfMonth}",
                        13f,
                        textColor,
                        day.isToday
                    )
                )
                addView(horizontalDivider())
                addView(calendarCellText(day.emotionEmoji, 27f, textColor))
                contentDescription = "${day.weekday}요일 ${day.dayOfMonth}일 ${day.emotionEmoji}"
                isEnabled = !day.isFuture
                isClickable = !day.isFuture
                isFocusable = !day.isFuture
                setOnClickListener {
                    if (!editMode && !day.isFuture) onCalendarDateClick(day.dateKey)
                }
            }
        }

        private fun calendarCellText(
            value: String,
            sizeSp: Float,
            color: Int,
            bold: Boolean = false
        ) = TextView(itemView.context).apply {
            text = value
            gravity = Gravity.CENTER
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        private fun horizontalDivider() = View(itemView.context).apply {
            setBackgroundColor(color(palette.stroke))
            alpha = 0.55f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        }

        private fun verticalDivider() = View(itemView.context).apply {
            setBackgroundColor(color(palette.stroke))
            alpha = 0.55f
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(82))
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }

    private fun stateColors(checked: Int, unchecked: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked, unchecked)
    )

    private fun widgetDisplay(type: String): Pair<String, String> = when (type) {
        "CALENDAR" -> "주간 캘린더" to ""
        "CHECKLIST" -> "체크리스트" to "☆ 기록 안 함\n★ 복습 완료"
        "TODO_LIST" -> "TodoList" to ""
        "YEARS_AGO" -> "1년 전 오늘" to "이런 일이 있었네요"
        "REMINDER" -> "오늘의 리마인더" to "그때의 기록을 다시 확인해 보세요"
        else -> type to "예시 위젯"
    }

    @ColorRes
    private fun widgetColor(type: String): Int = when (type) {
        "CALENDAR" -> palette.calendar
        "CHECKLIST" -> palette.checklist
        "TODO_LIST" -> palette.todo
        "YEARS_AGO" -> palette.yearsAgo
        "REMINDER" -> palette.reminder
        else -> palette.checklist
    }
}
