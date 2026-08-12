package com.example.colorpaper.ui.setting

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.databinding.FragmentRepeatCycleBinding
import com.example.colorpaper.reminder.ReminderNotification
import com.example.colorpaper.reminder.ReminderPreferences
import com.example.colorpaper.reminder.ReminderSchedulePolicy
import com.example.colorpaper.reminder.ReminderTemplate
import com.example.colorpaper.ui.theme.ThemeManager
import com.example.colorpaper.ui.theme.ThemedDialogStyler
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class RepeatCycleFragment : Fragment() {
    private var _binding: FragmentRepeatCycleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentRepeatCycleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.ivBackRepeatCycle.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.ivAddCustomCycle.setOnClickListener {
            editTemplate(ReminderTemplate(System.currentTimeMillis(), "새 복습", listOf(1), false), true)
        }
        binding.btnReminderTime.setOnClickListener { showTimePicker() }
        binding.btnTestReminder.setOnClickListener { sendTestReminder() }
        applyTheme()
        renderTemplates()
        renderTime()
    }

    private fun sendTestReminder() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val diary = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(appContext).diaryDao()
                    .getReminderEnabledDiaries()
                    .firstOrNull { item ->
                        ReminderSchedulePolicy.elapsedDays(
                            item.reviewCycleDays,
                            item.reminderStage,
                            item.reviewCyclePattern,
                            item.reviewRepeatLast
                        ) != null
                    }
            }
            if (!isAdded) return@launch
            if (diary == null) {
                Toast.makeText(
                    requireContext(),
                    "먼저 반복주기가 설정된 메모지를 저장해 주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            ReminderNotification.show(appContext, diary, diary.reminderStage)
            Toast.makeText(
                requireContext(),
                "테스트 알림을 보냈어요. 알림을 눌러 홈 화면도 확인해 보세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun renderTemplates() {
        val templates = ReminderPreferences.templates(requireContext())
        val palette = ThemeManager.currentPalette(requireContext())
        val textColor = color(palette.primaryText)
        val pastel = ColorUtils.blendARGB(color(palette.screenBackground), color(palette.reminder), .72f)
        binding.llCustomCyclesContainer.removeAllViews()
        templates.forEach { template ->
            val card = MaterialCardView(requireContext()).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = ColorUtils.setAlphaComponent(textColor, 35)
                setCardBackgroundColor(pastel)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            }
            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                addView(TextView(context).apply {
                    text = template.name
                    textSize = 16f
                    setTextColor(textColor)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = template.days.joinToString("  →  ") { "${it}일" }
                    textSize = 14f
                    setTextColor(textColor)
                    setPadding(0, dp(5), 0, 0)
                })
                addView(TextView(context).apply {
                    text = if (template.repeatLast) "마지막 간격으로 계속 반복" else "마지막 알림 후 중지"
                    textSize = 12f
                    setTextColor(ColorUtils.setAlphaComponent(textColor, 165))
                    setPadding(0, dp(4), 0, 0)
                })
            }
            card.addView(content)
            card.setOnClickListener { editTemplate(template, false) }
            binding.llCustomCyclesContainer.addView(card)
        }
    }

    private fun editTemplate(initial: ReminderTemplate, isNew: Boolean) {
        val context = requireContext()
        val palette = ThemeManager.currentPalette(context)
        val pastel = ColorUtils.blendARGB(color(palette.screenBackground), color(palette.reminder), .72f)
        val days = initial.days.toMutableList().ifEmpty { mutableListOf(1) }
        val name = EditText(context).apply { setText(initial.name); hint = "템플릿 이름" }
        val dayRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val repeatGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val stop = RadioButton(context).apply { text = "마지막 알림 후 중지"; id = View.generateViewId() }
        val repeat = RadioButton(context).apply { text = "마지막 간격으로 계속 반복"; id = View.generateViewId() }
        repeatGroup.addView(stop); repeatGroup.addView(repeat)
        repeatGroup.check(if (initial.repeatLast) repeat.id else stop.id)

        fun drawDays() {
            dayRow.removeAllViews()
            days.forEachIndexed { index, value ->
                if (index > 0) dayRow.addView(TextView(context).apply {
                    text = "→"
                    gravity = Gravity.CENTER
                    setTextColor(color(palette.primaryText))
                }, LinearLayout.LayoutParams(dp(22), dp(46)))
                dayRow.addView(MaterialButton(context).apply {
                    text = "${value}일"
                    minWidth = 0
                    minHeight = 0
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(23)
                    setPadding(0, 0, 0, 0)
                    backgroundTintList = ColorStateList.valueOf(pastel)
                    setOnClickListener {
                        val input = EditText(context).apply { inputType = 2; setText(value.toString()) }
                        android.app.AlertDialog.Builder(context).setTitle("알림 날짜 수정")
                            .setView(input).setNegativeButton("취소", null)
                            .setPositiveButton("확인") { _, _ ->
                                input.text.toString().toIntOrNull()?.takeIf { it > 0 }?.let {
                                    days[index] = it; days.sort(); drawDays()
                                }
                            }.create().also(::styleDialog).show()
                    }
                }, LinearLayout.LayoutParams(dp(46), dp(46)))
            }
            dayRow.addView(MaterialButton(context).apply {
                text = "+"
                minWidth = 0
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(23)
                setPadding(0, 0, 0, 0)
                backgroundTintList = ColorStateList.valueOf(pastel)
                setOnClickListener {
                    val input = EditText(context).apply {
                        inputType = 2; hint = "${days.last() + 1}"; setText((days.last() + 1).toString())
                    }
                    android.app.AlertDialog.Builder(context).setTitle("다음 알림은 며칠째?")
                        .setView(input).setNegativeButton("취소", null)
                        .setPositiveButton("추가") { _, _ ->
                            input.text.toString().toIntOrNull()?.takeIf { it > days.last() }?.let {
                                days.add(it); drawDays()
                            }
                        }.create().also(::styleDialog).show()
                }
            }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) })
        }
        drawDays()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(16))
            addView(name)
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(dayRow)
            }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(22) })
            addView(repeatGroup, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(22)
                bottomMargin = dp(8)
            })
        }
        val builder = android.app.AlertDialog.Builder(context).setTitle(if (isNew) "새 템플릿" else "템플릿 편집")
            .setView(body).setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val all = ReminderPreferences.templates(context).toMutableList()
                val saved = initial.copy(name = name.text.toString().trim().ifBlank { "이름 없는 복습" }, days = days.distinct().sorted(), repeatLast = repeatGroup.checkedRadioButtonId == repeat.id)
                val index = all.indexOfFirst { it.id == saved.id }
                if (index >= 0) all[index] = saved else all.add(saved)
                ReminderPreferences.saveTemplates(context, all); renderTemplates()
            }
        if (!isNew) builder.setNeutralButton("삭제") { _, _ ->
            ReminderPreferences.saveTemplates(context, ReminderPreferences.templates(context).filterNot { it.id == initial.id })
            renderTemplates()
        }
        builder.create().also(::styleDialog).show()
    }

    private fun showTimePicker() {
        val context = requireContext()
        val palette = ThemeManager.currentPalette(context)
        val initialHour = ReminderPreferences.defaultHour(context)
        val initialMinute = ReminderPreferences.defaultMinute(context)
        val hourPicker = NumberPicker(context).apply {
            minValue = 1; maxValue = 12; value = (initialHour % 12).let { if (it == 0) 12 else it }
            wrapSelectorWheel = true
        }
        val minutePicker = NumberPicker(context).apply {
            minValue = 0; maxValue = 59; value = initialMinute; wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
        }
        val periodPicker = NumberPicker(context).apply {
            minValue = 0; maxValue = 1; displayedValues = arrayOf("오전", "오후")
            value = if (initialHour < 12) 0 else 1; wrapSelectorWheel = false
        }
        val textColor = color(palette.primaryText)
        val themedSurface = ColorUtils.blendARGB(color(palette.screenBackground), color(palette.reminder), .72f)
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            val card = MaterialCardView(context).apply {
                radius = dp(28).toFloat()
                strokeWidth = dp(1)
                strokeColor = ColorUtils.setAlphaComponent(textColor, 36)
                setCardBackgroundColor(color(palette.screenBackground))
                val body = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(22), dp(20), dp(18))
                    addView(TextView(context).apply {
                        text = "알림 받을 시간"; textSize = 19f; gravity = Gravity.CENTER
                        setTextColor(textColor)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        background = roundedBackground(themedSurface, 22)
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        addView(periodPicker, LinearLayout.LayoutParams(0, dp(142), 1f))
                        addView(hourPicker, LinearLayout.LayoutParams(0, dp(142), 1f))
                        addView(TextView(context).apply { text = ":"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(textColor) }, LinearLayout.LayoutParams(dp(20), dp(142)))
                        addView(minutePicker, LinearLayout.LayoutParams(0, dp(142), 1f))
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(18) })
                    addView(MaterialButton(context).apply {
                        text = "이 시간으로 할래요"
                        gravity = Gravity.CENTER
                        cornerRadius = dp(24)
                        backgroundTintList = ColorStateList.valueOf(themedSurface)
                        setTextColor(textColor)
                        setOnClickListener {
                            val selectedHour = (hourPicker.value % 12) + if (periodPicker.value == 1) 12 else 0
                            ReminderPreferences.saveDefaultTime(context, selectedHour, minutePicker.value)
                            renderTime()
                            dismiss()
                        }
                    })
                }; addView(body)
            }
            setContentView(card); window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); window?.setLayout((resources.displayMetrics.widthPixels * .86f).toInt(), -2)
        }
        dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * .86f).toInt(), -2)
    }

    private fun styleDialog(dialog: android.app.AlertDialog) {
        dialog.setOnShowListener {
            ThemedDialogStyler.apply(dialog, requireContext())
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun renderTime() { binding.btnReminderTime.text = formatTime(ReminderPreferences.defaultHour(requireContext()), ReminderPreferences.defaultMinute(requireContext())) }
    private fun formatTime(hour: Int, minute: Int): String { val p = if (hour < 12) "오전" else "오후"; val h = hour % 12; return String.format(Locale.getDefault(), "%s %d:%02d", p, if (h == 0) 12 else h, minute) }
    private fun applyTheme() {
        val p = ThemeManager.currentPalette(requireContext()); val bg = color(p.screenBackground); val text = color(p.primaryText); val surface = color(p.todo); val pastel = ColorUtils.blendARGB(bg, color(p.reminder), .72f)
        binding.root.setBackgroundColor(bg); tintText(binding.root, text); binding.ivBackRepeatCycle.imageTintList = ColorStateList.valueOf(text)
        listOf(binding.cvDefaultCycle, binding.cvReminderTime).forEach { it.setCardBackgroundColor(surface); it.strokeColor = ColorUtils.setAlphaComponent(text, 38); it.strokeWidth = dp(1) }
        listOf(binding.ivAddCustomCycle, binding.btnReminderTime, binding.btnTestReminder).forEach {
            it.backgroundTintList = ColorStateList.valueOf(pastel)
            it.setTextColor(text)
        }
    }
    private fun tintText(view: View, color: Int) { if (view is TextView) view.setTextColor(color); if (view is ViewGroup) repeat(view.childCount) { tintText(view.getChildAt(it), color) } }
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
