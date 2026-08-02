package com.example.colorpaper.ui.home

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.MainActivity
import com.example.colorpaper.data.model.WidgetEntity
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.ReminderAnswerEntity
import com.example.colorpaper.reminder.ReminderIntents
import com.example.colorpaper.reminder.MaskingText
import com.example.colorpaper.reminder.ReminderMessageFactory
import com.example.colorpaper.reminder.ReminderSchedulePolicy
import com.example.colorpaper.ui.calendar.EmotionStampFormatter
import com.example.colorpaper.ui.theme.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var adapter: HomeWidgetAdapter
    private var editMode = false
    private var allWidgets: List<WidgetEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_home_widgets)
        val editButton = view.findViewById<TextView>(R.id.btn_edit_layout)
        val dateTitle = view.findViewById<TextView>(R.id.tv_date_title)
        val palette = ThemeManager.currentPalette(requireContext())

        view.findViewById<View>(R.id.home_root).setBackgroundColor(
            ContextCompat.getColor(requireContext(), palette.screenBackground)
        )
        dateTitle.setTextColor(ContextCompat.getColor(requireContext(), palette.primaryText))
        editButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), palette.accent)
        )

        dateTitle.text = SimpleDateFormat("M월 d일", Locale.KOREAN).format(Date())
        allWidgets = defaultWidgets()
        adapter = HomeWidgetAdapter(emptyList(), palette) { dateKey ->
            (requireActivity() as? MainActivity)?.openDiaryDate(dateKey)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = editMode

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = adapter.moveItem(
                viewHolder.bindingAdapterPosition,
                target.bindingAdapterPosition
            )

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        editButton.setOnClickListener {
            if (editMode) saveLayout(editButton) else enterEditMode(editButton)
        }

        showHomeWidgets()

        val reminderDiaryId = arguments?.getInt(ReminderIntents.EXTRA_DIARY_ID, -1) ?: -1
        val reminderStage = arguments?.getInt(ReminderIntents.EXTRA_STAGE, -1) ?: -1
        if (reminderDiaryId > 0 && reminderStage >= 0) {
            showReminderDialog(reminderDiaryId, reminderStage)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadWeeklyCalendar()
    }

    private fun loadWeeklyCalendar() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val weekStart = Calendar.getInstance().apply {
            val offset = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_MONTH, -offset)
        }
        val weekdayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
        val todayKey = dateFormat.format(Date())
        val days = weekdayLabels.mapIndexed { index, label ->
            val date = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, index)
            }
            val dateKey = dateFormat.format(date.time)
            WeekDayMood(
                weekday = label,
                month = date.get(Calendar.MONTH) + 1,
                dayOfMonth = date.get(Calendar.DAY_OF_MONTH),
                dateKey = dateKey,
                isToday = dateKey == todayKey,
                isFuture = dateKey > todayKey
            )
        }
        adapter.updateCalendarDays(days)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val diaries = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).diaryDao().getDiariesBetween(
                    startDate = days.first().dateKey,
                    endDate = days.last().dateKey
                )
            }
            val emotionsByDate = diaries.groupBy { it.createdAt }.mapValues { (_, records) ->
                EmotionStampFormatter.format(
                    records.map { it.emotionStamp.orEmpty() },
                    maxCount = 1
                )
            }
            adapter.updateCalendarDays(
                days.map { day ->
                    day.copy(emotionEmoji = emotionsByDate[day.dateKey].orEmpty())
                }
            )
        }
    }

    private fun showReminderDialog(diaryId: Int, stage: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(requireContext()).diaryDao()
            val diary = withContext(Dispatchers.IO) { dao.getDiaryById(diaryId) }
                ?: return@launch
            val previousAnswer = withContext(Dispatchers.IO) {
                dao.getReminderAnswer(diaryId, stage)
            }
            val elapsedDays = ReminderSchedulePolicy.elapsedDays(
                diary.reviewCycleDays,
                stage
            ) ?: return@launch
            if (!isAdded) return@launch

            val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
            val question = ReminderMessageFactory.create(
                content = diary.content,
                emotions = diary.emotionStamp,
                stage = stage,
                elapsedDays = elapsedDays
            ).title
            dialogView.findViewById<TextView>(R.id.tv_reminder_question).text = question
            val recordText = dialogView.findViewById<TextView>(R.id.tv_reminder_record)
            val masking = MaskingText.create(
                diary.content.removePrefix("[DECO]:"),
                diary.highlightRanges
            )
            recordText.text = listOf(diary.createdAt, masking.masked).joinToString("\n")
            if (masking.hasMasks) bindMaskingView(dialogView, recordText, diary.createdAt, masking)
            val answerInput = dialogView.findViewById<EditText>(R.id.et_reminder_answer)
            answerInput.setText(previousAnswer?.answer.orEmpty())

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.reminder_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.reminder_later, null)
                .setPositiveButton(R.string.reminder_save, null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val answerText = answerInput.text.toString().trim()
                    if (answerText.isEmpty()) {
                        answerInput.error = getString(R.string.reminder_answer_required)
                        return@setOnClickListener
                    }
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        dao.saveReminderAnswer(
                            ReminderAnswerEntity(
                                answerId = previousAnswer?.answerId ?: 0,
                                diaryId = diaryId,
                                reminderStage = stage,
                                question = question,
                                answer = answerText
                            )
                        )
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.reminder_answer_saved,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    private fun bindMaskingView(
        dialogView: View,
        recordText: TextView,
        dateText: String,
        masking: MaskingText
    ) {
        val maskingLayout = dialogView.findViewById<View>(R.id.layout_reminder_masking)
        val modeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.group_masking_mode)
        val inputLayout = dialogView.findViewById<View>(R.id.layout_masking_input)
        val maskInput = dialogView.findViewById<EditText>(R.id.et_masking_answer)
        val feedback = dialogView.findViewById<TextView>(R.id.tv_masking_feedback)
        var answerVisible = false

        fun showRecord(content: String) {
            recordText.text = listOf(dateText, content).joinToString("\n")
        }

        maskingLayout.visibility = View.VISIBLE
        recordText.setOnClickListener {
            if (modeGroup.checkedRadioButtonId == R.id.radio_mask_touch) {
                answerVisible = !answerVisible
                showRecord(if (answerVisible) masking.original else masking.masked)
            }
        }
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            answerVisible = false
            showRecord(masking.masked)
            inputLayout.visibility = if (checkedId == R.id.radio_mask_input) {
                View.VISIBLE
            } else {
                View.GONE
            }
            feedback.visibility = View.GONE
        }
        dialogView.findViewById<View>(R.id.btn_masking_check).setOnClickListener {
            val correct = masking.matches(maskInput.text.toString())
            feedback.setText(if (correct) R.string.masking_correct else R.string.masking_incorrect)
            feedback.setTextColor(Color.parseColor(if (correct) "#2E7D32" else "#B3261E"))
            feedback.visibility = View.VISIBLE
            if (correct) showRecord(masking.original)
        }
    }

    private fun enterEditMode(editButton: TextView) {
        editMode = true
        editButton.text = "저장하기"
        adapter.setEditMode(true)
        adapter.updateData(allWidgets)
    }

    private fun saveLayout(editButton: TextView) {
        allWidgets = adapter.currentWidgets().mapIndexed { index, widget ->
            widget.copy(order = index)
        }
        editMode = false
        editButton.text = "편집"
        showHomeWidgets()
    }

    private fun showHomeWidgets() {
        adapter.setEditMode(false)
        adapter.updateData(allWidgets.filter { it.isVisible })
    }

    private fun defaultWidgets(): List<WidgetEntity> = listOf(
        WidgetEntity(userId = DEMO_USER_ID, type = "CALENDAR", isVisible = true, order = 0),
        WidgetEntity(userId = DEMO_USER_ID, type = "CHECKLIST", isVisible = true, order = 1),
        WidgetEntity(userId = DEMO_USER_ID, type = "TODO_LIST", isVisible = true, order = 2),
        WidgetEntity(userId = DEMO_USER_ID, type = "YEARS_AGO", isVisible = true, order = 3),
        WidgetEntity(userId = DEMO_USER_ID, type = "REMINDER", isVisible = true, order = 4)
    )

    companion object {
        private const val DEMO_USER_ID = 1

        fun newInstance(diaryId: Int, stage: Int) = HomeFragment().apply {
            arguments = Bundle().apply {
                putInt(ReminderIntents.EXTRA_DIARY_ID, diaryId)
                putInt(ReminderIntents.EXTRA_STAGE, stage)
            }
        }
    }
}
