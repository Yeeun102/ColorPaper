package com.example.colorpaper.ui.calendar

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.colorpaper.R
import com.example.colorpaper.MainActivity
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.repository.UserRepository
import com.example.colorpaper.databinding.FragmentMonthlyCalendarBinding
import com.example.colorpaper.ui.diary.DiaryFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MonthlyCalendarFragment : Fragment() {

    private var _binding: FragmentMonthlyCalendarBinding? = null
    private val binding get() = _binding!!

    private val displayedMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }

    private var selectedDate: String? = null
    private lateinit var adapter: MonthCalendarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonthlyCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTheme()
        setupRecyclerView()
        setupListeners()
    }

    private fun setupTheme() {
        val safeContext = context ?: return
        val palette = ThemeManager.currentPalette(safeContext)

        binding.root.setBackgroundColor(ContextCompat.getColor(safeContext, palette.screenBackground))
        binding.tvCalendarMonth.setTextColor(ContextCompat.getColor(safeContext, palette.primaryText))
        binding.tvCalendarSelectionTitle.setTextColor(ContextCompat.getColor(safeContext, palette.primaryText))
        binding.cardCalendarSelection.setCardBackgroundColor(ContextCompat.getColor(safeContext, palette.checklist))
        binding.cardCalendarSelection.strokeColor = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(safeContext, palette.primaryText),
            38
        )
        binding.cardCalendarSelection.strokeWidth = maxOf(1, resources.displayMetrics.density.toInt())
        binding.btnCalendarRecord.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(safeContext, palette.accent)
        )

        adapter = MonthCalendarAdapter(palette, ::selectDate)
        binding.rvCalendarDays.adapter = adapter
    }

    private fun setupRecyclerView() {
        binding.rvCalendarDays.layoutManager = GridLayoutManager(requireContext(), 7)
    }

    private fun setupListeners() {
        binding.btnCalendarPrevious.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            selectedDate = null
            hideSelectionBar()
            loadMonth()
        }

        binding.btnCalendarNext.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            selectedDate = null
            hideSelectionBar()
            loadMonth()
        }

        binding.btnCalendarRecord.setOnClickListener {
            val date = selectedDate ?: return@setOnClickListener
            (requireActivity() as? MainActivity)?.openDiaryDate(date)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            loadMonth()
        }
    }

    private fun loadMonth() {
        val safeContext = context?.applicationContext ?: return
        val monthTime = displayedMonth.time

        // UI 텍스트 갱신 (메인 스레드)
        val monthTitleFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
        binding.tvCalendarMonth.text = monthTitleFormat.format(monthTime)

        viewLifecycleOwner.lifecycleScope.launch {
            // 💡 DB 조회 + 날짜 격자 UI 데이터 생성을 모두 백그라운드 스레드에서 연산
            val uiDays = withContext(Dispatchers.IO) {
                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.KOREAN)
                val monthKey = monthKeyFormat.format(monthTime)

                val database = AppDatabase.getDatabase(safeContext)
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                val diaries = if (userId.isNullOrBlank()) {
                    database.diaryDao().getDiariesForMonth(monthKey)
                } else {
                    UserRepository(database)
                        .getPublicDiariesByUserId(userId)
                        .filter { it.createdAt.startsWith(monthKey) }
                }
                val emotionsByDate = diaries.groupBy { it.createdAt }.mapValues { (_, records) ->
                    EmotionStampFormatter.format(records.map { it.emotionStamp.orEmpty() })
                }

                buildMonthDays(emotionsByDate, diaries.map { it.createdAt }.toSet())
            }

            if (_binding != null) {
                adapter.submitDays(uiDays, selectedDate)
            }
        }
    }

    // Dispatchers.IO 전용 계산 함수
    private fun buildMonthDays(
        emotionsByDate: Map<String, String>,
        recordedDates: Set<String>
    ): List<MonthDayUi> {
        val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)

        val firstDay = displayedMonth.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)

        val leadingEmptyDays = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val lastDay = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayKey = dateKeyFormat.format(Date())

        val cells = MutableList(leadingEmptyDays) { MonthDayUi(null, null) }

        for (day in 1..lastDay) {
            firstDay.set(Calendar.DAY_OF_MONTH, day)
            val dateKey = dateKeyFormat.format(firstDay.time)
            val hasRecord = dateKey in recordedDates
            val emotion = emotionsByDate[dateKey].orEmpty()

            cells += MonthDayUi(
                dateKey = dateKey,
                dayNumber = day,
                emotionStamps = if (hasRecord && emotion.isBlank()) {
                    DEFAULT_EMOTION_EMOJI
                } else {
                    emotion
                },
                isToday = dateKey == todayKey,
                hasRecord = hasRecord
            )
        }

        while (cells.size % 7 != 0) {
            cells += MonthDayUi(null, null)
        }

        return cells
    }

    private fun selectDate(day: MonthDayUi) {
        val dateKey = day.dateKey ?: return
        val dayNumber = day.dayNumber ?: return
        selectedDate = dateKey

        binding.tvCalendarSelectionTitle.text = getString(
            R.string.calendar_selected_date_format,
            dayNumber,
            day.emotionStamps
        ).trim()

        if (binding.cardCalendarSelection.visibility != View.VISIBLE) {
            binding.cardCalendarSelection.visibility = View.VISIBLE
            binding.cardCalendarSelection.post {
                _binding?.let { b ->
                    b.cardCalendarSelection.animate().cancel() // 진행 중인 애니메이션 취소
                    b.cardCalendarSelection.translationY = b.cardCalendarSelection.height.toFloat()
                    b.cardCalendarSelection.animate()
                        .translationY(0f)
                        .setDuration(180L)
                        .start()
                }
            }
        }
    }

    private fun hideSelectionBar() {
        if (_binding == null) return
        binding.cardCalendarSelection.animate().cancel()
        binding.cardCalendarSelection.visibility = View.GONE
        binding.cardCalendarSelection.translationY = 0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 메모리 누수 방지
    }

    companion object {
        private const val DEFAULT_EMOTION_EMOJI = "🙂"
    }
}
