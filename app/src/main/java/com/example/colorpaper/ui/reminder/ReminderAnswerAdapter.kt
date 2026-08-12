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
import com.example.colorpaper.ui.theme.ThemePalette
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReminderHistoryItem(
    val diaryId: Int,
    val stage: Int,
    val question: String,
    val diaryContent: String,
    val diaryCreatedAt: String,
    val answer: String? = null,
    val answeredAt: Long = 0L
) {
    val pending: Boolean get() = answer == null
}

class ReminderAnswerAdapter(
    private val palette: ThemePalette,
    private val onAnswerClick: (ReminderHistoryItem) -> Unit,
    private val onSavedAnswerClick: (ReminderHistoryItem) -> Unit,
    private val onRecordClick: (String) -> Unit
) : RecyclerView.Adapter<ReminderAnswerAdapter.AnswerViewHolder>() {
    private val items = mutableListOf<ReminderHistoryItem>()

    fun submitItems(newItems: List<ReminderHistoryItem>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AnswerViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_reminder_answer, parent, false)
    )
    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class AnswerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val answerCard = view.findViewById<MaterialCardView>(R.id.card_reminder_answer)
        private val questionCard = view.findViewById<MaterialCardView>(R.id.card_reminder_question)
        private val question = view.findViewById<TextView>(R.id.tv_answer_question)
        private val recordDate = view.findViewById<TextView>(R.id.tv_answer_record_date)
        private val record = view.findViewById<TextView>(R.id.tv_answer_record)
        private val answerTime = view.findViewById<TextView>(R.id.tv_answer_time)
        private val answerText = view.findViewById<TextView>(R.id.tv_answer_text)
        private val divider = view.findViewById<View>(R.id.divider_reminder_answer)
        private val action = view.findViewById<MaterialButton>(R.id.btn_reminder_record_action)

        fun bind(item: ReminderHistoryItem) {
            val context = itemView.context
            val textColor = ContextCompat.getColor(context, palette.primaryText)
            val screenColor = ContextCompat.getColor(context, palette.screenBackground)
            val reminderColor = ContextCompat.getColor(context, palette.reminder)
            val checklistColor = ContextCompat.getColor(context, palette.checklist)
            val brightAction = ColorUtils.blendARGB(
                screenColor, reminderColor, .32f
            )
            answerCard.setCardBackgroundColor(ColorUtils.blendARGB(screenColor, checklistColor, .28f))
            answerCard.strokeColor = ColorUtils.setAlphaComponent(textColor, 38)
            answerCard.strokeWidth = maxOf(1, context.resources.displayMetrics.density.toInt())
            questionCard.setCardBackgroundColor(ColorUtils.blendARGB(screenColor, reminderColor, .34f))
            divider.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, palette.stroke))
            listOf(question, recordDate, record, answerTime, answerText).forEach { it.setTextColor(textColor) }

            question.text = item.question
            recordDate.text = formatRecordDate(item.diaryCreatedAt)
            record.text = item.diaryContent.removePrefix("[DECO]:").ifBlank {
                context.getString(R.string.reminder_record_missing)
            }
            answerText.text = item.answer.orEmpty()
            answerText.visibility = if (item.pending) View.GONE else View.VISIBLE
            answerTime.text = if (item.pending) "답변 전" else answerTimeFormat.format(Date(item.answeredAt))
            action.text = if (item.pending) "답변하기" else "기록 보기"
            action.backgroundTintList = ColorStateList.valueOf(brightAction)
            action.setTextColor(textColor)
            action.setOnClickListener {
                if (item.pending) onAnswerClick(item) else onRecordClick(item.diaryCreatedAt)
            }
            answerCard.setOnClickListener {
                if (item.pending) onAnswerClick(item) else onSavedAnswerClick(item)
            }
        }

        private fun formatRecordDate(dateKey: String): String = runCatching {
            recordDateFormat.format(dateKeyFormat.parse(dateKey) ?: return@runCatching dateKey)
        }.getOrDefault(dateKey)
    }

    companion object {
        private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        private val recordDateFormat = SimpleDateFormat("M월 d일", Locale.KOREAN)
        private val answerTimeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    }
}
