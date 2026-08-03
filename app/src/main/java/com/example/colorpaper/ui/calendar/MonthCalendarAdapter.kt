package com.example.colorpaper.ui.calendar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.ui.theme.ThemePalette

data class MonthDayUi(
    val dateKey: String?,
    val dayNumber: Int?,
    val emotionStamps: String = "",
    val isToday: Boolean = false
)

class MonthCalendarAdapter(
    private val palette: ThemePalette,
    private val onDateClick: (MonthDayUi) -> Unit
) : RecyclerView.Adapter<MonthCalendarAdapter.DayViewHolder>() {
    private val days = mutableListOf<MonthDayUi>()
    private var selectedDate: String? = null

    fun submitDays(newDays: List<MonthDayUi>, selected: String?) {
        days.clear()
        days.addAll(newDays)
        selectedDate = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) = holder.bind(days[position])

    override fun getItemCount(): Int = days.size

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayText: TextView = itemView.findViewById(R.id.tv_calendar_day)
        private val emotions: TextView = itemView.findViewById(R.id.tv_calendar_emotions)

        fun bind(day: MonthDayUi) {
            val context = itemView.context
            val textColor = ContextCompat.getColor(context, palette.primaryText)
            dayText.text = day.dayNumber?.toString().orEmpty()
            emotions.text = day.emotionStamps
            dayText.setTextColor(textColor)
            emotions.setTextColor(textColor)
            itemView.isEnabled = day.dateKey != null
            itemView.alpha = if (day.dateKey == null) 0f else 1f

            val selected = day.dateKey != null && day.dateKey == selectedDate
            itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * context.resources.displayMetrics.density
                setColor(
                    when {
                        selected -> ContextCompat.getColor(context, palette.reminder)
                        day.isToday -> ContextCompat.getColor(context, palette.calendar)
                        else -> Color.TRANSPARENT
                    }
                )
                if (selected || day.isToday) {
                    setStroke(1, ContextCompat.getColor(context, palette.stroke))
                }
            }
            itemView.setOnClickListener {
                if (day.dateKey != null) {
                    selectedDate = day.dateKey
                    notifyDataSetChanged()
                    onDateClick(day)
                }
            }
        }
    }
}
