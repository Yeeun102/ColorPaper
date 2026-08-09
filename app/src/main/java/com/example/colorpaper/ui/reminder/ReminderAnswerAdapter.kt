package com.example.colorpaper.ui.reminder

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.model.ReminderAnswerWithDiary
import com.example.colorpaper.ui.theme.ThemePalette
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAnswerAdapter(
    private val palette: ThemePalette,
    private val onRecordClick: (String) -> Unit
) : RecyclerView.Adapter<ReminderAnswerAdapter.AnswerViewHolder>() {

    private val items = mutableListOf<ReminderAnswerWithDiary>()

    fun submitItems(newItems: List<ReminderAnswerWithDiary>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder_answer, parent, false)
        return AnswerViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AnswerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val answerCard = view.findViewById<MaterialCardView>(R.id.card_reminder_answer)
        private val questionCard = view.findViewById<MaterialCardView>(R.id.card_reminder_question)
        private val question = view.findViewById<TextView>(R.id.tv_answer_question)
        private val recordDate = view.findViewById<TextView>(R.id.tv_answer_record_date)
        private val record = view.findViewById<TextView>(R.id.tv_answer_record)
        private val answerTime = view.findViewById<TextView>(R.id.tv_answer_time)
        private val answerText = view.findViewById<TextView>(R.id.tv_answer_text)
        private val divider = view.findViewById<View>(R.id.divider_reminder_answer)

        fun bind(item: ReminderAnswerWithDiary) {
            val context = itemView.context
            val textColor = ContextCompat.getColor(context, palette.primaryText)
            val dateKey = item.diaryCreatedAt

            answerCard.setCardBackgroundColor(ContextCompat.getColor(context, palette.checklist))
            answerCard.strokeColor = ColorUtils.setAlphaComponent(textColor, 38)
            answerCard.strokeWidth = maxOf(1, context.resources.displayMetrics.density.toInt())
            questionCard.setCardBackgroundColor(ContextCompat.getColor(context, palette.reminder))
            divider.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, palette.stroke)
            )
            listOf(question, recordDate, record, answerTime, answerText).forEach {
                it.setTextColor(textColor)
            }

            question.text = item.reminderAnswer.question
            recordDate.text = formatRecordDate(dateKey)
            record.text = item.diaryContent
                ?.removePrefix("[DECO]:")
                ?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.reminder_record_missing)
            answerText.text = item.reminderAnswer.answer
            answerTime.text = answerTimeFormat.format(Date(item.reminderAnswer.answeredAt))

            val canOpenRecord = !dateKey.isNullOrBlank()
            answerCard.isClickable = canOpenRecord
            answerCard.isFocusable = canOpenRecord
            answerCard.setOnClickListener {
                if (canOpenRecord) onRecordClick(dateKey.orEmpty())
            }
        }

        private fun formatRecordDate(dateKey: String?): String {
            if (dateKey.isNullOrBlank()) return ""
            return runCatching {
                val parsed = dateKeyFormat.parse(dateKey) ?: return@runCatching dateKey
                recordDateFormat.format(parsed)
            }.getOrDefault(dateKey)
        }
    }

    companion object {
        private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        private val recordDateFormat = SimpleDateFormat("M월 d일", Locale.KOREAN)
        private val answerTimeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    }
}
