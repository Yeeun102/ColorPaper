package com.example.colorpaper.ui.reminder

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.ui.theme.ThemeManager
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
        emptyCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), palette.accent))
        recyclerView = view.findViewById(R.id.rv_reminder_answers)
        adapter = ReminderAnswerAdapter(palette) { dateKey ->
            (requireActivity() as? MainActivity)?.openDiaryDate(dateKey)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
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
            val answers = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).diaryDao().getReminderAnswersBetween(
                    userId = userId,
                    startOfDay = startOfToday.timeInMillis,
                    startOfNextDay = startOfTomorrow.timeInMillis
                )
            }
            if (!isAdded) return@launch
            adapter.submitItems(answers)
            title.text = getString(R.string.reminder_history_title_count, answers.size)
            val isEmpty = answers.isEmpty()
            emptyCard.visibility = if (isEmpty) View.VISIBLE else View.GONE
            recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }
}
