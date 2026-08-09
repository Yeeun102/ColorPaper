package com.example.colorpaper.ui.home

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.model.TodoEntity
import com.example.colorpaper.data.model.WidgetEntity
import com.example.colorpaper.ui.theme.ThemePalette
import com.google.android.material.card.MaterialCardView
import java.util.Collections

class HomeWidgetAdapter(
    widgets: List<WidgetEntity>,
    private val palette: ThemePalette,
    private val onCalendarDateClick: (String) -> Unit = {},
    private val onTodoAdd: (String, Boolean) -> Unit = { _, _ -> },
    private val onTodoCompletionChange: (TodoEntity, Boolean) -> Unit = { _, _ -> },
    private val onTodoMoveToTomorrow: (TodoEntity) -> Unit = {},
    private val onWidgetSizeChange: (Set<String>) -> Unit = {}
) : RecyclerView.Adapter<HomeWidgetAdapter.WidgetViewHolder>() {

    private val widgetList = widgets.toMutableList()
    private var editMode = false
    private var calendarDays: List<WeekDayMood> = emptyList()
    private var todoItems: List<TodoEntity> = emptyList()
    private var yearsAgo: YearsAgoUi? = null
    private var hasTodayRecord = false
    private var hasTodayReview = false
    private val largeWidgetTypes = mutableSetOf<String>()

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

    fun updateTodoItems(items: List<TodoEntity>) {
        todoItems = items
        notifyWidgetChanged("TODO_LIST")
    }

    fun updateYearsAgo(item: YearsAgoUi?) {
        yearsAgo = item
        notifyWidgetChanged("YEARS_AGO")
    }

    fun updateTodayChecklist(hasRecord: Boolean, hasReview: Boolean) {
        hasTodayRecord = hasRecord
        hasTodayReview = hasReview
        notifyWidgetChanged("CHECKLIST")
    }

    fun setLargeWidgetTypes(types: Set<String>) {
        largeWidgetTypes.clear()
        largeWidgetTypes.addAll(types)
        notifyDataSetChanged()
    }

    private fun notifyWidgetChanged(type: String) {
        val position = widgetList.indexOfFirst { it.type == type }
        if (position >= 0) notifyItemChanged(position)
    }

    inner class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view as MaterialCardView
        private val title: TextView = view.findViewById(R.id.tv_widget_title)
        private val content: TextView = view.findViewById(R.id.tv_widget_content)
        private val visibilitySwitch: SwitchCompat = view.findViewById(R.id.switch_widget_visibility)
        private val dragHandle: TextView = view.findViewById(R.id.tv_drag_handle)
        private val sizeButton: TextView = view.findViewById(R.id.tv_widget_size)
        private val todoLayout: LinearLayout = view.findViewById(R.id.layout_todo_items)
        private val todoDynamic: LinearLayout = view.findViewById(R.id.layout_todo_dynamic)
        private val todoInput: EditText = view.findViewById(R.id.et_home_todo)
        private val todoAdd: TextView = view.findViewById(R.id.btn_home_todo_add)
        private val todoCarryNew: CheckBox = view.findViewById(R.id.cb_home_todo_carry)
        private val weeklyCalendar: LinearLayout = view.findViewById(R.id.layout_week_calendar)
        private val checklistLayout: LinearLayout = view.findViewById(R.id.layout_checklist_items)
        private val checklistPending: TextView = view.findViewById(R.id.tv_checklist_pending)
        private val checklistCompleted: TextView = view.findViewById(R.id.tv_checklist_completed)
        private val checklistPendingIcon: ImageView = view.findViewById(R.id.iv_checklist_pending)
        private val checklistCompletedIcon: ImageView =
            view.findViewById(R.id.iv_checklist_completed)

        fun bind(widget: WidgetEntity) {
            val display = widgetDisplay(widget.type)
            val textColor = color(palette.primaryText)
            val accentColor = color(palette.accent)
            val offColor = color(palette.switchOff)

            card.setCardBackgroundColor(color(widgetColor(widget.type)))
            card.strokeWidth = 0
            title.text = when (widget.type) {
                "YEARS_AGO" -> yearsAgo?.let { "${it.yearsAgo}년 전 오늘" } ?: display.first
                else -> display.first
            }
            content.text = when (widget.type) {
                "YEARS_AGO" -> yearsAgo?.preview
                    ?: itemView.context.getString(R.string.years_ago_empty)
                else -> display.second
            }
            title.setTextColor(textColor)
            content.setTextColor(textColor)
            dragHandle.setTextColor(textColor)
            sizeButton.setTextColor(textColor)
            todoInput.setTextColor(textColor)
            todoInput.setHintTextColor(ColorStateList.valueOf(offColor))
            todoAdd.setTextColor(textColor)
            todoCarryNew.setTextColor(textColor)
            todoCarryNew.buttonTintList = stateColors(accentColor, color(palette.stroke))
            checklistPending.setTextColor(textColor)
            checklistCompleted.setTextColor(textColor)
            checklistPendingIcon.setImageResource(
                if (hasTodayRecord) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            checklistPendingIcon.imageTintList = ColorStateList.valueOf(
                if (hasTodayRecord) accentColor else textColor
            )
            checklistCompletedIcon.setImageResource(
                if (hasTodayReview) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            checklistCompletedIcon.imageTintList = ColorStateList.valueOf(
                if (hasTodayReview) accentColor else textColor
            )

            visibilitySwitch.thumbTintList = ColorStateList.valueOf(color(palette.textOnAccent))
            visibilitySwitch.trackTintList = stateColors(accentColor, offColor)

            val isTodo = widget.type == "TODO_LIST"
            val isCalendar = widget.type == "CALENDAR"
            val isChecklist = widget.type == "CHECKLIST"
            content.visibility = if (editMode || isTodo || isCalendar || isChecklist) {
                View.GONE
            } else {
                View.VISIBLE
            }
            checklistLayout.visibility = if (!editMode && isChecklist) View.VISIBLE else View.GONE
            weeklyCalendar.visibility = if (!editMode && isCalendar) View.VISIBLE else View.GONE
            if (isCalendar && !editMode) bindWeeklyCalendar(textColor)
            todoLayout.visibility = if (!editMode && isTodo) View.VISIBLE else View.GONE
            if (isTodo && !editMode) bindTodoItems(textColor, accentColor)
            visibilitySwitch.visibility = if (editMode) View.VISIBLE else View.GONE
            sizeButton.visibility = if (editMode) View.VISIBLE else View.GONE
            dragHandle.visibility = if (editMode) View.VISIBLE else View.GONE
            card.minimumHeight = if (widget.type in largeWidgetTypes) dp(178) else 0
            sizeButton.text = itemView.context.getString(
                if (widget.type in largeWidgetTypes) {
                    R.string.home_widget_size_large
                } else {
                    R.string.home_widget_size_default
                }
            )
            sizeButton.setOnClickListener {
                if (widget.type in largeWidgetTypes) {
                    largeWidgetTypes.remove(widget.type)
                } else {
                    largeWidgetTypes.add(widget.type)
                }
                onWidgetSizeChange(largeWidgetTypes.toSet())
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let(::notifyItemChanged)
            }

            card.isClickable = false
            card.isFocusable = false
            card.isLongClickable = false
            card.isCheckable = false
            card.setRippleColor(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT))
            card.setOnClickListener(null)
            card.setOnLongClickListener(null)

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
            }
            calendarDays.forEachIndexed { index, day ->
                weeklyCalendar.addView(createDayCell(day, textColor))
                if (index < calendarDays.lastIndex) {
                    weeklyCalendar.addView(verticalDivider())
                }
            }
        }

        private fun bindTodoItems(textColor: Int, accentColor: Int) {
            todoDynamic.removeAllViews()
            if (todoItems.isEmpty()) {
                todoDynamic.addView(TextView(itemView.context).apply {
                    text = itemView.context.getString(R.string.home_todo_empty)
                    setTextColor(textColor)
                    alpha = 0.72f
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(0, dp(5), 0, dp(5))
                })
            } else {
                todoItems.forEach { todo -> todoDynamic.addView(todoRow(todo, textColor, accentColor)) }
            }

            fun addTodo() {
                val value = todoInput.text.toString().trim()
                if (value.isBlank()) return
                onTodoAdd(value, todoCarryNew.isChecked)
                todoInput.text?.clear()
            }
            todoAdd.setOnClickListener { addTodo() }
            todoInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    addTodo()
                    true
                } else {
                    false
                }
            }
        }

        private fun todoRow(todo: TodoEntity, textColor: Int, accentColor: Int) =
            LinearLayout(itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(CheckBox(context).apply {
                    text = todo.content
                    isChecked = todo.isCompleted
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    buttonTintList = stateColors(accentColor, color(palette.stroke))
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    setOnCheckedChangeListener { _, checked ->
                        onTodoCompletionChange(todo, checked)
                    }
                })
                addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_arrow_forward)
                    imageTintList = ColorStateList.valueOf(textColor)
                    contentDescription = context.getString(R.string.home_todo_move_tomorrow)
                    isEnabled = !todo.isCompleted
                    visibility = if (todo.isCompleted) View.GONE else View.VISIBLE
                    background = null
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                    )
                    setOnClickListener { onTodoMoveToTomorrow(todo) }
                })
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
                addView(calendarCellText(day.emotionEmoji, 25f, textColor))
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
        "CHECKLIST" -> "체크리스트" to ""
        "TODO_LIST" -> "TodoList" to ""
        "YEARS_AGO" -> "1년 전 오늘" to "이런 일이 있었네요"
        "REMINDER" -> "오늘의 리마인더" to "오늘의 답변을 다시 확인해보세요"
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
