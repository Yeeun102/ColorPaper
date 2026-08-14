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
import androidx.core.graphics.ColorUtils
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
import com.example.colorpaper.data.model.TodoEntity
import com.example.colorpaper.data.repository.UserRepository
import com.example.colorpaper.data.repository.ReminderAnswerStore
import com.example.colorpaper.data.repository.DiaryRepository
import com.example.colorpaper.reminder.ReminderIntents
import com.example.colorpaper.reminder.PendingReminder
import com.example.colorpaper.reminder.ReminderInbox
import com.example.colorpaper.reminder.MaskingText
import com.example.colorpaper.reminder.ReminderMessageFactory
import com.example.colorpaper.reminder.ReminderSchedulePolicy
import com.example.colorpaper.reminder.ReminderScheduler
import com.example.colorpaper.ui.calendar.EmotionStampFormatter
import com.example.colorpaper.ui.theme.ThemeManager
import com.example.colorpaper.ui.theme.ThemedDialogStyler
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private enum class ReminderPromptMode { QUESTION, QUIZ, RESOLUTION }

    private lateinit var adapter: HomeWidgetAdapter
    private var editMode = false
    private var allWidgets: List<WidgetEntity> = emptyList()
    private var pendingReminders: List<PendingReminder> = emptyList()
    private var diarySyncStarted = false

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
        allWidgets = currentLocalUserId()?.let(::defaultWidgets).orEmpty()
        adapter = HomeWidgetAdapter(
            widgets = emptyList(),
            palette = palette,
            onCalendarDateClick = ::openDiaryDate,
            onCalendarWidgetClick = ::openMonthlyCalendar,
            onTodoAdd = ::addTodo,
            onTodoCompletionChange = ::updateTodoCompletion,
            onTodoMoveToTomorrow = ::moveTodoToTomorrow,
            onReminderClick = ::openReminderHistory,
            onYearsAgoClick = ::openDiaryDate,
            onWidgetSizeChange = ::saveWidgetSizes
        )
        adapter.setLargeWidgetTypes(loadWidgetSizes())
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
        loadWidgetLayout()

        val reminderDiaryId = arguments?.getInt(ReminderIntents.EXTRA_DIARY_ID, -1) ?: -1
        val reminderStage = arguments?.getInt(ReminderIntents.EXTRA_STAGE, -1) ?: -1
        syncRemoteDiaries(reminderDiaryId, reminderStage)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            loadWeeklyCalendar()
            loadTodos()
            loadYearsAgoRecord()
            loadTodayChecklist()
            loadPendingReminders()
        }
    }

    private fun loadPendingReminders(openNext: Boolean = false) {
        val userId = com.example.colorpaper.util.AuthUtils.getCurrentUserId()
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(appContext).diaryDao()
                val pending = ReminderInbox.pendingToday(dao, userId)
                pending
            }
            if (!isAdded) return@launch
            pendingReminders = result
            adapter.updatePendingReminders(
                pendingCount = result.size,
                preview = result.firstOrNull()?.let {
                    ReminderMessageFactory.create(
                        it.diary.content, it.diary.emotionStamp, it.stage, it.elapsedDays
                    ).title
                }.orEmpty()
            )
            if (openNext) result.firstOrNull()?.let {
                showReminderDialog(it.diary.diaryId, it.stage)
            }
        }
    }

    private fun openPendingReminder() {
        pendingReminders.firstOrNull()?.let {
            showReminderDialog(it.diary.diaryId, it.stage)
        }
    }

    private fun openDiaryDate(dateKey: String) {
        (requireActivity() as? MainActivity)?.openDiaryDate(dateKey)
    }

    private fun openMonthlyCalendar() {
        (requireActivity() as? MainActivity)?.openMonthlyCalendar()
    }

    private fun openReminderHistory() {
        (requireActivity() as? MainActivity)?.openReminderHistory()
    }

    private fun loadTodos() {
        val localUserId = currentLocalUserId() ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val todos = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(appContext).todoDao()
                dao.reassignLegacyUser(LEGACY_LOCAL_USER_ID, localUserId)
                val todayItems = dao.getTodosForDate(localUserId, today)
                val existingContents = todayItems.map { it.content.trim() }.toMutableSet()
                dao.getCarryOverCandidates(localUserId, today)
                    .distinctBy { it.content.trim() }
                    .forEach { previous ->
                        if (existingContents.add(previous.content.trim())) {
                            dao.insertTodo(
                                previous.copy(
                                    todoId = 0,
                                    isCompleted = false,
                                    targetDate = today
                                )
                            )
                        }
                        dao.updateCarryOver(previous.todoId, false)
                    }
                dao.getTodosForDate(localUserId, today)
            }
            if (isAdded) adapter.updateTodoItems(todos)
        }
    }

    private fun addTodo(content: String, carryOver: Boolean) {
        val localUserId = currentLocalUserId() ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).todoDao().insertTodo(
                    TodoEntity(
                        userId = localUserId,
                        content = content,
                        targetDate = today,
                        carryOver = carryOver
                    )
                )
            }
            loadTodos()
        }
    }

    private fun updateTodoCompletion(todo: TodoEntity, completed: Boolean) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).todoDao()
                    .updateCompletion(todo.todoId, completed)
            }
            loadTodos()
        }
    }

    private fun moveTodoToTomorrow(todo: TodoEntity) {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val tomorrowKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(tomorrow.time)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).todoDao()
                    .moveToDate(todo.todoId, tomorrowKey)
            }
            loadTodos()
            val root = view ?: return@launch
            Snackbar.make(root, R.string.home_todo_moved_tomorrow, Snackbar.LENGTH_LONG)
                .setAction(R.string.home_todo_undo) {
                    undoTodoMove(todo)
                }
                .show()
        }
    }

    private fun undoTodoMove(todo: TodoEntity) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).todoDao()
                    .moveToDate(todo.todoId, todo.targetDate)
            }
            loadTodos()
        }
    }

    private fun loadYearsAgoRecord() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val diary = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).diaryDao().getLatestDiaryFromSameDay(
                    userId = userId,
                    monthAndDay = today.substring(5),
                    today = today
                )
            }
            val item = diary?.let {
                val currentYear = today.substringBefore('-').toIntOrNull()
                val recordYear = it.createdAt.substringBefore('-').toIntOrNull()
                if (currentYear == null || recordYear == null) return@let null
                YearsAgoUi(
                    yearsAgo = currentYear - recordYear,
                    dateKey = it.createdAt,
                    preview = it.content.removePrefix("[DECO]:").take(120)
                )
            }
            if (isAdded) adapter.updateYearsAgo(item)
        }
    }

    private fun loadTodayChecklist() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            adapter.updateTodayChecklist(hasRecord = false, hasReview = false)
            return
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfNextDay = (startOfDay.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val appContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(appContext)
                val hasRecord = database.diaryDao()
                    .getPostItsByDateAndUserId(today, userId)
                    .isNotEmpty()
                val hasReminderAnswer = database.diaryDao().getReminderAnswersBetween(
                    userId = userId,
                    startOfDay = startOfDay.timeInMillis,
                    startOfNextDay = startOfNextDay.timeInMillis
                ).isNotEmpty()
                val hasFlashcardAnswer = database.flashcardDao().countReviewedCardsBetween(
                    userId = userId,
                    startOfDay = startOfDay.timeInMillis,
                    startOfNextDay = startOfNextDay.timeInMillis
                ) > 0
                hasRecord to (hasReminderAnswer || hasFlashcardAnswer)
            }
            if (isAdded) {
                adapter.updateTodayChecklist(
                    hasRecord = status.first,
                    hasReview = status.second
                )
            }
        }
    }

    private fun loadWidgetLayout() {
        val localUserId = currentLocalUserId() ?: return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val widgets = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(appContext).widgetDao()
                dao.reassignLegacyUser(LEGACY_LOCAL_USER_ID, localUserId)
                val saved = dao.getWidgetsByUser(localUserId)
                if (saved.isNotEmpty()) {
                    saved
                } else {
                    dao.insertWidgets(defaultWidgets(localUserId))
                    dao.getWidgetsByUser(localUserId)
                }
            }
            if (!isAdded) return@launch
            allWidgets = widgets
            showHomeWidgets()
        }
    }

    private fun loadWidgetSizes(): Set<String> {
        val preferences = requireContext().getSharedPreferences(
            HOME_PREFERENCES, android.content.Context.MODE_PRIVATE
        )
        val accountKey = widgetSizesKey()
        if (preferences.contains(accountKey)) {
            return preferences.getStringSet(accountKey, emptySet()).orEmpty()
        }
        val legacySizes = preferences.getStringSet(KEY_LARGE_WIDGETS, emptySet()).orEmpty()
        preferences.edit()
            .putStringSet(accountKey, legacySizes)
            .remove(KEY_LARGE_WIDGETS)
            .apply()
        return legacySizes
    }

    private fun syncRemoteDiaries(reminderDiaryId: Int = -1, reminderStage: Int = -1) {
        if (diarySyncStarted) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        diarySyncStarted = true
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val syncSucceeded = withContext(Dispatchers.IO) {
                runCatching {
                    DiaryRepository(appContext).syncUserDiariesFromRemote(userId)
                    val dao = AppDatabase.getDatabase(appContext).diaryDao()
                    ReminderAnswerStore(dao).syncUserAnswers(userId)
                }.onFailure {
                    android.util.Log.e("HomeFragment", "Firestore 다이어리 복원 실패", it)
                }.isSuccess
            }
            if (!syncSucceeded) diarySyncStarted = false
            if (!isAdded) return@launch
            loadWeeklyCalendar()
            loadYearsAgoRecord()
            loadTodayChecklist()
            loadPendingReminders()
            if (reminderDiaryId > 0 && reminderStage >= 0) {
                showReminderDialog(reminderDiaryId, reminderStage)
            }
        }
    }

    private fun saveWidgetSizes(types: Set<String>) {
        if (currentLocalUserId() == null) return
        requireContext().getSharedPreferences(HOME_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .edit()
            .putStringSet(widgetSizesKey(), types)
            .apply()
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
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        viewLifecycleOwner.lifecycleScope.launch {
            val diaries = withContext(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(appContext)
                val byUser = userId?.let { uid ->
                    UserRepository(database)
                        .getPublicDiariesByUserId(uid)
                        .filter { it.createdAt in days.first().dateKey..days.last().dateKey }
                }.orEmpty()
                byUser
            }
            val emotionsByDate = diaries.groupBy { it.createdAt }.mapValues { (_, records) ->
                EmotionStampFormatter.format(
                    records.map { it.emotionStamp.orEmpty() },
                    maxCount = 1
                )
            }
            adapter.updateCalendarDays(
                days.map { day ->
                    val emotion = emotionsByDate[day.dateKey].orEmpty()
                    val hasRecord = diaries.any { it.createdAt == day.dateKey }
                    day.copy(
                        emotionEmoji = if (hasRecord && emotion.isBlank()) {
                            DEFAULT_EMOTION_EMOJI
                        } else {
                            emotion
                        }
                    )
                }
            )
        }
    }

    private fun showReminderDialog(diaryId: Int, stage: Int) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(requireContext()).diaryDao()
            val diary = withContext(Dispatchers.IO) { dao.getDiaryById(diaryId) }
                ?: return@launch
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            if (diary.userId != userId) return@launch
            val answerStore = ReminderAnswerStore(dao)
            val currentAnswer = withContext(Dispatchers.IO) {
                answerStore.get(userId, diaryId, stage)
            }
            val previousAnswer = withContext(Dispatchers.IO) {
                answerStore.getLatestBeforeStage(userId, diaryId, stage)
            }
            val elapsedDays = ReminderSchedulePolicy.elapsedDays(
                diary.reviewCycleDays,
                stage,
                diary.reviewCyclePattern,
                diary.reviewRepeatLast
            ) ?: return@launch
            if (!isAdded) return@launch

            val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
            var question = previousAnswer?.question ?: ReminderMessageFactory.create(
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
            recordText.text = listOf(diary.createdAt, masking.original).joinToString("\n")
            val answerInput = dialogView.findViewById<EditText>(R.id.et_reminder_answer)
            val modes = if (previousAnswer != null) {
                listOf(ReminderPromptMode.QUESTION)
            } else buildList {
                add(ReminderPromptMode.QUESTION)
                if (masking.hasMasks) add(ReminderPromptMode.QUIZ)
                if (ReminderMessageFactory.isDifficultEmotion(diary.emotionStamp)) {
                    add(ReminderPromptMode.RESOLUTION)
                }
            }
            val promptMode = modes[Math.floorMod(diaryId * 31 + stage, modes.size)]
            dialogView.findViewById<View>(R.id.layout_reminder_masking).visibility = View.GONE
            dialogView.findViewById<View>(R.id.layout_resolution_choices).visibility = View.GONE
            answerInput.visibility = View.GONE
            when (promptMode) {
                ReminderPromptMode.QUESTION -> {
                    answerInput.visibility = View.VISIBLE
                    answerInput.setText(currentAnswer?.answer.orEmpty())
                }
                ReminderPromptMode.QUIZ -> {
                    question = "빈칸에 들어갈 말은 무엇일까요?"
                    dialogView.findViewById<TextView>(R.id.tv_reminder_question).text = question
                    recordText.text = listOf(diary.createdAt, masking.masked).joinToString("\n")
                    bindQuizView(dialogView, recordText, diary.createdAt, masking, answerInput, diaryId, stage)
                }
                ReminderPromptMode.RESOLUTION -> {
                    question = "그 때의 감정, 해결됐나요?"
                    dialogView.findViewById<TextView>(R.id.tv_reminder_question).text = question
                    dialogView.findViewById<View>(R.id.layout_resolution_choices).visibility = View.VISIBLE
                }
            }
            val resolvedYes = dialogView.findViewById<MaterialButton>(R.id.btn_resolved_yes).apply {
                text = "해결했어요"
            }
            val resolvedNo = dialogView.findViewById<MaterialButton>(R.id.btn_resolved_no).apply {
                text = "아직이에요"
            }
            val palette = ThemeManager.currentPalette(requireContext())
            val screenColor = ContextCompat.getColor(requireContext(), palette.screenBackground)
            val reminderColor = ContextCompat.getColor(requireContext(), palette.reminder)
            val accentColor = ContextCompat.getColor(requireContext(), palette.accent)
            val normalChoiceColor = ColorUtils.blendARGB(screenColor, reminderColor, .42f)
            val selectedChoiceColor = ColorUtils.blendARGB(screenColor, accentColor, .82f)
            fun selectResolution(selected: MaterialButton, other: MaterialButton) {
                selected.backgroundTintList = ColorStateList.valueOf(selectedChoiceColor)
                other.backgroundTintList = ColorStateList.valueOf(normalChoiceColor)
                selected.alpha = 1f
                other.alpha = .78f
            }
            resolvedYes.setOnClickListener {
                answerInput.setText("해결했어요")
                selectResolution(resolvedYes, resolvedNo)
            }
            resolvedNo.setOnClickListener {
                answerInput.setText("아직이에요")
                selectResolution(resolvedNo, resolvedYes)
            }

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.reminder_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.reminder_later, null)
                .setPositiveButton(R.string.reminder_save, null)
                .create()

            dialog.setOnShowListener {
                ThemedDialogStyler.applyWidePopup(dialog, requireContext())
                var currentAnswerSaved = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (currentAnswerSaved) {
                        dialog.dismiss()
                        loadPendingReminders(openNext = true)
                        return@setOnClickListener
                    }
                    val answerText = answerInput.text.toString().trim()
                    if (answerText.isEmpty()) {
                        answerInput.error = getString(R.string.reminder_answer_required)
                        return@setOnClickListener
                    }
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            answerStore.save(
                                userId = userId,
                                diary = diary,
                                answer = ReminderAnswerEntity(
                                    answerId = currentAnswer?.answerId ?: 0,
                                    diaryId = diaryId,
                                    reminderStage = stage,
                                    question = question,
                                    answer = answerText
                                )
                            )
                        } catch (error: Exception) {
                            android.util.Log.e("HomeFragment", "리마인드 질문/답변 서버 저장 실패", error)
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        "서버 DB 저장에 실패했습니다. 네트워크를 확인해 주세요.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            return@launch
                        }
                        val latestDiary = dao.getDiaryById(diaryId)
                        if (latestDiary?.reminderStage == stage) {
                            val answeredAt = System.currentTimeMillis()
                            val nextAnchorAt = ReminderSchedulePolicy.anchorAfterAnswer(
                                answeredAt = answeredAt,
                                cycleDays = latestDiary.reviewCycleDays,
                                answeredStage = stage,
                                cyclePattern = latestDiary.reviewCyclePattern,
                                repeatLast = latestDiary.reviewRepeatLast
                            )
                            dao.advanceReminderAfterAnswer(
                                diaryId = diaryId,
                                answeredAt = answeredAt,
                                nextStage = stage + 1,
                                nextAnchorAt = nextAnchorAt
                            )
                            dao.getDiaryById(diaryId)?.let {
                                ReminderScheduler.schedule(appContext, it)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.reminder_answer_saved,
                                    Toast.LENGTH_SHORT
                                ).show()
                                loadTodayChecklist()
                                if (previousAnswer == null) {
                                    loadPendingReminders(openNext = true)
                                }
                            }
                            if (previousAnswer == null) {
                                dialog.dismiss()
                            } else {
                                currentAnswerSaved = true
                                dialogView.findViewById<View>(
                                    R.id.layout_previous_reminder_answer
                                ).visibility = View.VISIBLE
                                dialogView.findViewById<TextView>(
                                    R.id.tv_previous_reminder_question
                                ).text = "질문: ${previousAnswer.question}"
                                dialogView.findViewById<TextView>(
                                    R.id.tv_previous_reminder_answer
                                ).text = "답변: ${previousAnswer.answer}"
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "닫기"
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).visibility = View.GONE
                            }
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    private fun bindQuizView(
        dialogView: View,
        recordText: TextView,
        dateText: String,
        masking: MaskingText,
        answerInput: EditText,
        diaryId: Int,
        stage: Int
    ) {
        val maskingLayout = dialogView.findViewById<View>(R.id.layout_reminder_masking)
        val modeGroup = dialogView.findViewById<View>(R.id.group_masking_mode)
        val inputLayout = dialogView.findViewById<View>(R.id.layout_masking_input)
        val choiceLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.layout_masking_choices)
        val feedback = dialogView.findViewById<TextView>(R.id.tv_masking_feedback)
        val palette = ThemeManager.currentPalette(requireContext())
        val screenColor = ContextCompat.getColor(requireContext(), palette.screenBackground)
        val reminderColor = ContextCompat.getColor(requireContext(), palette.reminder)
        val accentColor = ContextCompat.getColor(requireContext(), palette.accent)
        val normalChoiceColor = ColorUtils.blendARGB(screenColor, reminderColor, .42f)
        val selectedChoiceColor = ColorUtils.blendARGB(screenColor, accentColor, .82f)
        val choiceButtons = mutableListOf<MaterialButton>()
        maskingLayout.visibility = View.VISIBLE
        modeGroup.visibility = View.GONE
        inputLayout.visibility = View.GONE
        choiceLayout.visibility = View.VISIBLE
        choiceLayout.removeAllViews()

        val answer = masking.answers.firstOrNull().orEmpty()
        val seed = diaryId * 31 + stage
        val distractors = masking.original
            .split(Regex("[^가-힣A-Za-z0-9]+"))
            .map(String::trim)
            .filter { it.isNotBlank() && it != answer }
            .distinct()
            .shuffled(kotlin.random.Random(seed))
            .toMutableList()
        listOf("기억이 안 나요", "다른 내용", "아직 모르겠어요").forEach {
            if (it != answer && it !in distractors) distractors += it
        }
        (listOf(answer) + distractors.take(2))
            .shuffled(kotlin.random.Random(seed + 1))
            .forEach { option ->
                val choiceButton = MaterialButton(requireContext()).apply {
                    text = option
                    cornerRadius = (18 * resources.displayMetrics.density).toInt()
                    setOnClickListener {
                        choiceButtons.forEach { button ->
                            val selected = button === this
                            button.backgroundTintList = ColorStateList.valueOf(
                                if (selected) selectedChoiceColor else normalChoiceColor
                            )
                            button.alpha = if (selected) 1f else .78f
                        }
                        val correct = option == answer
                        answerInput.setText(option)
                        feedback.text = if (correct) "정답이에요" else "다시 생각해 보세요"
                        feedback.setTextColor(Color.parseColor(if (correct) "#2E7D32" else "#B3261E"))
                        feedback.visibility = View.VISIBLE
                        if (correct) {
                            recordText.text = listOf(dateText, masking.original).joinToString("\n")
                        }
                    }
                }
                choiceButtons += choiceButton
                choiceLayout.addView(choiceButton)
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
        val choiceLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.layout_masking_choices)
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
            choiceLayout.visibility = if (checkedId == R.id.radio_mask_choice) View.VISIBLE else View.GONE
            feedback.visibility = View.GONE
        }
        dialogView.findViewById<View>(R.id.btn_masking_check).setOnClickListener {
            val correct = masking.matches(maskInput.text.toString())
            feedback.setText(if (correct) R.string.masking_correct else R.string.masking_incorrect)
            feedback.setTextColor(Color.parseColor(if (correct) "#2E7D32" else "#B3261E"))
            feedback.visibility = View.VISIBLE
            if (correct) showRecord(masking.original)
        }

        val answer = masking.answers.firstOrNull().orEmpty()
        val distractors = masking.original
            .split(Regex("[^가-힣A-Za-z0-9]+"))
            .filter { it.isNotBlank() && it != answer }
            .distinct()
            .shuffled()
            .take(2)
        (distractors + answer).shuffled().forEach { option ->
            choiceLayout.addView(com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = option
                cornerRadius = (18 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    val correct = option == answer
                    feedback.text = if (correct) "정답이에요!" else "다시 생각해 보세요."
                    feedback.setTextColor(Color.parseColor(if (correct) "#2E7D32" else "#B3261E"))
                    feedback.visibility = View.VISIBLE
                    if (correct) showRecord(masking.original)
                }
            })
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
        val widgetsToSave = allWidgets
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(appContext).widgetDao().insertWidgets(widgetsToSave)
        }
    }

    private fun showHomeWidgets() {
        adapter.setEditMode(false)
        adapter.updateData(allWidgets.filter { it.isVisible })
    }

    private fun currentLocalUserId(): Int? = FirebaseAuth.getInstance().currentUser?.uid?.hashCode()

    private fun widgetSizesKey(): String =
        "${KEY_LARGE_WIDGETS}_${FirebaseAuth.getInstance().currentUser?.uid.orEmpty()}"

    private fun defaultWidgets(userId: Int): List<WidgetEntity> = listOf(
        WidgetEntity(userId = userId, type = "CALENDAR", isVisible = true, order = 0),
        WidgetEntity(userId = userId, type = "CHECKLIST", isVisible = true, order = 1),
        WidgetEntity(userId = userId, type = "TODO_LIST", isVisible = true, order = 2),
        WidgetEntity(userId = userId, type = "YEARS_AGO", isVisible = true, order = 3),
        WidgetEntity(userId = userId, type = "REMINDER", isVisible = true, order = 4)
    )

    companion object {
        private const val HOME_PREFERENCES = "home_widget_preferences"
        private const val KEY_LARGE_WIDGETS = "large_widget_types"
        private const val LEGACY_LOCAL_USER_ID = 1
        private const val DEFAULT_EMOTION_EMOJI = "🙂"

        fun newInstance(diaryId: Int, stage: Int) = HomeFragment().apply {
            arguments = Bundle().apply {
                putInt(ReminderIntents.EXTRA_DIARY_ID, diaryId)
                putInt(ReminderIntents.EXTRA_STAGE, stage)
            }
        }
    }
}
