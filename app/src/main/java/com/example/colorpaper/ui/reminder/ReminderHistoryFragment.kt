package com.example.colorpaper.ui.reminder

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.reminder.ReminderInbox
import com.example.colorpaper.reminder.ReminderMessageFactory
import com.example.colorpaper.ui.theme.ThemeManager
import com.example.colorpaper.ui.theme.ThemedDialogStyler
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderHistoryFragment : Fragment() {

    private lateinit var adapter: ReminderAnswerAdapter
    private lateinit var title: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyCard: MaterialCardView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reminder_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val palette = ThemeManager.currentPalette(requireContext())
        val textColor = ContextCompat.getColor(requireContext(), palette.primaryText)

        view.findViewById<View>(R.id.reminder_history_root).setBackgroundColor(
            ContextCompat.getColor(requireContext(), palette.screenBackground)
        )
        title = view.findViewById(R.id.tv_reminder_history_title)
        title.setTextColor(textColor)
        view.findViewById<ImageButton>(R.id.btn_reminder_back).apply {
            imageTintList = ColorStateList.valueOf(textColor)
            setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        emptyCard = view.findViewById(R.id.card_reminder_empty)
        emptyCard.setCardBackgroundColor(
            ColorUtils.blendARGB(
                ContextCompat.getColor(requireContext(), palette.screenBackground),
                ContextCompat.getColor(requireContext(), palette.reminder),
                .34f
            )
        )
        emptyCard.findViewById<TextView>(R.id.tv_reminder_empty).setTextColor(textColor)
        recyclerView = view.findViewById(R.id.rv_reminder_answers)
        adapter = ReminderAnswerAdapter(
            palette = palette,
            onAnswerClick = { item ->
                (requireActivity() as? MainActivity)?.openReminder(item.diaryId, item.stage)
            },
            onSavedAnswerClick = ::showSavedAnswer,
            onRecordClick = { dateKey ->
                (requireActivity() as? MainActivity)?.openDiaryDate(dateKey)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun showSavedAnswer(item: ReminderHistoryItem) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("오늘의 답변")
            .setMessage(item.answer.orEmpty())
            .setPositiveButton("확인", null)
            .create()
        dialog.setOnShowListener { ThemedDialogStyler.apply(dialog, requireContext()) }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadTodayAnswers()
    }

    private fun loadTodayAnswers() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfTomorrow = (startOfToday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val appContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(appContext).diaryDao()
                val pending = ReminderInbox.pendingToday(dao, userId).map { item ->
                    ReminderHistoryItem(
                        diaryId = item.diary.diaryId,
                        stage = item.stage,
                        question = ReminderMessageFactory.create(
                            item.diary.content, item.diary.emotionStamp, item.stage, item.elapsedDays
                        ).title,
                        diaryContent = item.diary.content,
                        diaryCreatedAt = item.diary.createdAt
                    )
                }
                val answered = dao.getReminderAnswersBetween(
                    userId = userId,
                    startOfDay = startOfToday.timeInMillis,
                    startOfNextDay = startOfTomorrow.timeInMillis
                ).map { answer ->
                    ReminderHistoryItem(
                        diaryId = answer.reminderAnswer.diaryId,
                        stage = answer.reminderAnswer.reminderStage,
                        question = answer.reminderAnswer.question,
                        diaryContent = answer.diaryContent.orEmpty(),
                        diaryCreatedAt = answer.diaryCreatedAt.orEmpty(),
                        answer = answer.reminderAnswer.answer,
                        answeredAt = answer.reminderAnswer.answeredAt
                    )
                }
                pending + answered
            }
            if (!isAdded) return@launch
            adapter.submitItems(items)
            title.text = getString(R.string.reminder_history_title_count, items.size)
            val isEmpty = items.isEmpty()
            emptyCard.visibility = if (isEmpty) View.VISIBLE else View.GONE
            recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }
}
