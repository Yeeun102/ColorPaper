package com.example.colorpaper.ui.home

import android.content.res.ColorStateList
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
    private val palette: ThemePalette
) : RecyclerView.Adapter<HomeWidgetAdapter.WidgetViewHolder>() {

    private val widgetList = widgets.toMutableList()
    private var editMode = false
    private val todoChecked = mutableListOf(false, false)

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

    inner class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view as MaterialCardView
        private val title: TextView = view.findViewById(R.id.tv_widget_title)
        private val content: TextView = view.findViewById(R.id.tv_widget_content)
        private val visibilitySwitch: SwitchCompat = view.findViewById(R.id.switch_widget_visibility)
        private val dragHandle: TextView = view.findViewById(R.id.tv_drag_handle)
        private val todoLayout: LinearLayout = view.findViewById(R.id.layout_todo_items)
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
            content.visibility = if (editMode || isTodo) View.GONE else View.VISIBLE
            todoLayout.visibility = if (!editMode && isTodo) View.VISIBLE else View.GONE
            visibilitySwitch.visibility = if (editMode) View.VISIBLE else View.GONE
            dragHandle.visibility = if (editMode) View.VISIBLE else View.GONE

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
    }

    private fun stateColors(checked: Int, unchecked: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked, unchecked)
    )

    private fun widgetDisplay(type: String): Pair<String, String> = when (type) {
        "CALENDAR" -> "주간 캘린더" to "월   화   수   목   금   토   일\n😊   🤓   😥"
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
