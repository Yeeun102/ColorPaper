package com.example.colorpaper.ui.calendar

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.ui.diary.DiaryFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonthlyCalendarFragment : Fragment() {
    private val displayedMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.KOREAN)
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
    private val monthTitleFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
    private var selectedDate: String? = null
    private lateinit var adapter: MonthCalendarAdapter
    private lateinit var monthTitle: TextView
    private lateinit var selectionBar: MaterialCardView
    private lateinit var selectionTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_monthly_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val palette = ThemeManager.currentPalette(requireContext())
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), palette.screenBackground))

        monthTitle = view.findViewById(R.id.tv_calendar_month)
        selectionBar = view.findViewById(R.id.card_calendar_selection)
        selectionTitle = view.findViewById(R.id.tv_calendar_selection_title)
        val recordButton = view.findViewById<MaterialButton>(R.id.btn_calendar_record)
        monthTitle.setTextColor(ContextCompat.getColor(requireContext(), palette.primaryText))
        selectionTitle.setTextColor(ContextCompat.getColor(requireContext(), palette.primaryText))
        selectionBar.setCardBackgroundColor(ContextCompat.getColor(requireContext(), palette.checklist))
        selectionBar.strokeColor = ContextCompat.getColor(requireContext(), palette.stroke)
        recordButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), palette.accent)
        )

        adapter = MonthCalendarAdapter(palette, ::selectDate)
        view.findViewById<RecyclerView>(R.id.rv_calendar_days).apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = this@MonthlyCalendarFragment.adapter
        }
        view.findViewById<View>(R.id.btn_calendar_previous).setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            selectedDate = null
            hideSelectionBar()
            loadMonth()
        }
        view.findViewById<View>(R.id.btn_calendar_next).setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            selectedDate = null
            hideSelectionBar()
            loadMonth()
        }
        recordButton.setOnClickListener {
            val date = selectedDate ?: return@setOnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DiaryFragment.newInstance(date))
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadMonth()
    }

    private fun loadMonth() {
        val monthKey = monthKeyFormat.format(displayedMonth.time)
        monthTitle.text = monthTitleFormat.format(displayedMonth.time)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val diaries = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).diaryDao().getDiariesForMonth(monthKey)
            }
            val emotionsByDate = diaries.groupBy { it.createdAt }.mapValues { (_, records) ->
                EmotionStampFormatter.format(records.map { it.emotionStamp.orEmpty() })
            }
            adapter.submitDays(buildMonthDays(emotionsByDate), selectedDate)
        }
    }

    private fun buildMonthDays(emotionsByDate: Map<String, String>): List<MonthDayUi> {
        val firstDay = displayedMonth.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val leadingEmptyDays = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val lastDay = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayKey = dateKeyFormat.format(Date())
        val cells = MutableList(leadingEmptyDays) { MonthDayUi(null, null) }
        for (day in 1..lastDay) {
            val date = firstDay.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, day)
            val dateKey = dateKeyFormat.format(date.time)
            cells += MonthDayUi(
                dateKey = dateKey,
                dayNumber = day,
                emotionStamps = emotionsByDate[dateKey].orEmpty(),
                isToday = dateKey == todayKey
            )
        }
        while (cells.size % 7 != 0) cells += MonthDayUi(null, null)
        return cells
    }

    private fun selectDate(day: MonthDayUi) {
        val dateKey = day.dateKey ?: return
        val dayNumber = day.dayNumber ?: return
        selectedDate = dateKey
        selectionTitle.text = getString(
            R.string.calendar_selected_date_format,
            dayNumber,
            day.emotionStamps
        ).trim()
        if (selectionBar.visibility != View.VISIBLE) {
            selectionBar.visibility = View.VISIBLE
            selectionBar.post {
                selectionBar.translationY = selectionBar.height.toFloat()
                selectionBar.animate().translationY(0f).setDuration(180L).start()
            }
        }
    }

    private fun hideSelectionBar() {
        selectionBar.visibility = View.GONE
        selectionBar.translationY = 0f
    }
}
