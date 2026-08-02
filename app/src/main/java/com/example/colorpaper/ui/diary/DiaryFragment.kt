package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.graphics.Color
import androidx.fragment.app.Fragment
import android.text.Spanned
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentDiaryBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.HighlightEntity
import com.example.colorpaper.reminder.ReminderSchedulePolicy
import com.example.colorpaper.reminder.ReminderScheduler
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

class DiaryFragment : Fragment() {
    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!
    private val selectedTags = mutableSetOf<String>()
    private var isHighlightedState: Boolean = false
    private var currentVisibility: String = "PRIVATE"
    private var selectedReminderCycleDays: Int = ReminderSchedulePolicy.DISABLED
    private var reminderSelectionTouched: Boolean = false

    // 컴파일 타임에 검증 가능한 SVG 에셋 리소스 맵
    private val postItResourceMap = mapOf(
        "orange" to R.drawable.post_orange,
        "yellow" to R.drawable.post_yellow,
        "green"  to R.drawable.post_green,
        "blue"   to R.drawable.post_blue
    )

    private var currentActivePostIt: View? = null // 현재 설정 중인 타겟 포스트잇 뷰
    private var currentSelectedColor: String = "yellow" // 기본값 노랑
    private var tempSelectedEmotions = mutableListOf<String>() // 팝업창에서 임시 선택한 감정 텍스트
    private val selectedEmotions = mutableListOf<String>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("M월 d일", Locale.getDefault())
    private var selectedDateCalendar = Calendar.getInstance()

    private var emotionColorMap: Map<Button, Int> = emptyMap()
    private var buttonColorMap: Map<Button, Int> = emptyMap()


    // 💡 버튼 선택(하이라이트 + 2dp 테두리) 및 원상복구 제어 유틸 함수
    private fun applyCustomButtonState(button: Button, isSelected: Boolean, originalColor: Int = 0) {
        if (button is MaterialButton) {
            val density = resources.displayMetrics.density
            val defaultColor = if (originalColor != 0) originalColor else (buttonColorMap[button] ?: "#EDEDED".toColorInt())

            if (isSelected) {
                button.backgroundTintList = ColorStateList.valueOf("#FFF59D".toColorInt())
                button.strokeColor = ColorStateList.valueOf("#000000".toColorInt())
                button.strokeWidth = (2 * density).toInt()
            } else {
                button.backgroundTintList = ColorStateList.valueOf(defaultColor)
                button.strokeColor = ColorStateList.valueOf("#000000".toColorInt())
                button.strokeWidth = (1 * density).toInt()
            }
        }
    }

    private fun setupSingleChoiceGroup(
        buttons: List<Button>,
        defaultSelectedView: Button
    ) {
        buttons.forEach { button ->
            // Set initial state
            val isDefault = (button == defaultSelectedView)
            applyCustomButtonState(button, isSelected = isDefault)

            // Set click listener
            button.setOnClickListener {
                buttons.forEach { btn ->
                    applyCustomButtonState(btn, isSelected = false)
                }
                applyCustomButtonState(button, isSelected = true)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString(ARG_INITIAL_DATE)?.let { initialDate ->
            dateFormat.parse(initialDate)?.let { selectedDate ->
                selectedDateCalendar.time = selectedDate
            }
        }

        buttonColorMap = mapOf(
            binding.chipTagDaily to "#FFCDCD".toColorInt(),
            binding.chipTagWork to "#EECDFF".toColorInt(),
            binding.btnSelectEmotion to "#EDEDED".toColorInt(),
            binding.btnRepeatAuto to "#ECECEC".toColorInt(),
            binding.btnRepeatUser to "#D2FFDA".toColorInt(),
            binding.btnRepeatNone to "#F0E2B4".toColorInt(),
            binding.btnEndDateNone to "#F0E2B4".toColorInt(),
            binding.btnEndDateUser to "#D2FFDA".toColorInt(),
            binding.btnVisibilityPublic to "#DFD5FF".toColorInt(),
            binding.btnVisibilityFriendOnly to "#D7E7FF".toColorInt(),
            binding.btnVisibilityPrivate to "#FFD7D7".toColorInt()
        )

        emotionColorMap = mapOf(
            binding.emo1 to "#FFFDD0".toColorInt(),
            binding.emo2 to "#FFB348".toColorInt(),
            binding.emo3 to "#FFA4C8".toColorInt(),
            binding.emo4 to "#E1FF48".toColorInt(),
            binding.emo5 to "#BEFFBA".toColorInt(),
            binding.emo6 to "#FFDDBD".toColorInt(),
            binding.emo7 to "#FF0004".toColorInt(),
            binding.emo8 to "#C0E1D2".toColorInt(),
            binding.emo9 to "#BACFFF".toColorInt(),
            binding.emo10 to "#DFBAFF".toColorInt(),
            binding.emo11 to "#DC73FF".toColorInt(),
            binding.emo12 to "#C7F0FF".toColorInt()
        )
        // 초기 날짜 텍스트 세팅 및 오늘 데이터 로드
        updateDateText()
        loadTodayDiary()
        // 상단 달력 버튼 리스너 세팅
        binding.btnDatePicker.setOnClickListener {
            showDatePicker()
        }
        // 1. 플러스 버튼 누르면 새 메모지 생성 및 개별 설정창 노출
        binding.btnToolbarAdd.setOnClickListener {
            binding.tvEmptyHint.visibility = View.GONE
            // 기본값 초기화 후 새 필드 생성
            currentSelectedColor = "yellow"
            resetPostItSettingUI()
            addNewPostItField(currentSelectedColor)
        }
        // 💡 2. 형광펜 버튼 (btnToolbarPen) 클릭 처리
        binding.btnToolbarPen.setOnClickListener {
            applyHighlightToSelectedText()
        }

        // 💡 3. 텍스트 추가 버튼 (btnToolbarText) 클릭 처리
        binding.btnToolbarText.setOnClickListener {
            showAddDirectTextDialog()
        }

        // 2. 설정창 내부 색상 서클 클릭 시 -> 실시간 메모지 SVG 파일 교체
        binding.viewColorOrange.setOnClickListener { updateActivePostItColor("orange") }
        binding.viewColorYellow.setOnClickListener { updateActivePostItColor("yellow") }
        binding.viewColorGreen.setOnClickListener  { updateActivePostItColor("green") }
        binding.viewColorBlue.setOnClickListener   { updateActivePostItColor("blue") }

        // 3. 설정창 내부에서 '감정 선택하기' 클릭 시 팝업 활성화
        binding.btnSelectEmotion.setOnClickListener {
            openEmotionPopup()
        }

        // 4. 팝업 내부의 감정 칩 리스너 매핑 (#기뻐요 예시 및 기타 감정 확장 구조)
        val emotionClicker = View.OnClickListener { v ->
            if (v is Button) {
                val emotionText = v.text.toString().replace("#", "")
                val defaultColor = emotionColorMap[v] ?: "#EDEDED".toColorInt()

                if (tempSelectedEmotions.contains(emotionText)) {
                    tempSelectedEmotions.remove(emotionText)
                    // 선택 해제: 본래 고유 색상 + 1dp 테두리로 원상복귀
                    applyEmotionButtonState(v, isSelected = false, originalColor = defaultColor)
                } else {
                    tempSelectedEmotions.add(emotionText)
                    // 선택: 노란색 배경 + 2dp 테두리 피드백
                    applyEmotionButtonState(v, isSelected = true)
                }
            }
        }
        emotionColorMap.keys.forEach { button ->
            button.setOnClickListener(emotionClicker)
        }

        binding.btnEmotionPopupConfirm.setOnClickListener {
            binding.layoutEmotionPopup.visibility = View.GONE
            selectedEmotions.clear()
            selectedEmotions.addAll(tempSelectedEmotions)

            renderSelectedEmotionsInSetting()
        }

        binding.emo1.setOnClickListener(emotionClicker)
        binding.emo2.setOnClickListener(emotionClicker)
        binding.emo3.setOnClickListener(emotionClicker)
        binding.emo4.setOnClickListener(emotionClicker)
        binding.emo5.setOnClickListener(emotionClicker)
        binding.emo6.setOnClickListener(emotionClicker)
        binding.emo7.setOnClickListener(emotionClicker)
        binding.emo8.setOnClickListener(emotionClicker)
        binding.emo9.setOnClickListener(emotionClicker)
        binding.emo10.setOnClickListener(emotionClicker)
        binding.emo11.setOnClickListener(emotionClicker)
        binding.emo12.setOnClickListener(emotionClicker)

        // 5. 감정 팝업창에서 '확인' 누르면 칩 형태로 설정창에 주입
        binding.btnEmotionPopupConfirm.setOnClickListener {
            binding.layoutEmotionPopup.visibility = View.GONE
            selectedEmotions.clear()
            selectedEmotions.addAll(tempSelectedEmotions)

            renderSelectedEmotionsInSetting()
        }
        val tagSelectListener = View.OnClickListener { v ->
            if (v is Button) {
                val tagName = v.text.toString().replace("#", "")
                if (selectedTags.contains(tagName)) {
                    selectedTags.remove(tagName)
                    applyCustomButtonState(v, isSelected = false)
                } else {
                    selectedTags.add(tagName)
                    applyCustomButtonState(v, isSelected = true)
                }
            }
        }
        binding.chipTagDaily.setOnClickListener(tagSelectListener)
        binding.chipTagWork.setOnClickListener(tagSelectListener)

        binding.btnAddCustomTag.setOnClickListener {
            showAddTagDialog()
        }

        // 6. [핵심] 설정창에서 '저장' 클릭 시 완전 잠금, DB 실제 적재, 자유 드래그 기믹 가동
        binding.btnSettingSave.setOnClickListener {
            currentActivePostIt?.let { postIt ->
                val etContent = postIt.findViewById<EditText>(R.id.etPostItContent)
                val contentText = etContent.text.toString().trim()

                if (contentText.isNotBlank()) {
                    // 더이상 텍스트 수정 못하도록 원천 차단
                    etContent.isEnabled = false
                    etContent.isFocusable = false
                    etContent.clearFocus()
                    // 메모지 위치를 자유롭게 옮길 수 있도록 드래그 리스너 부착
                    makeViewDraggable(postIt)
                    // DB 최종 저장 처리 호출
                    saveCurrentDiaryWithPosition()
                } else {
                    Toast.makeText(requireContext(), "내용을 입력해야 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // (1) 공개여부 토글 버튼
        binding.btnVisibility.setOnClickListener {
            currentVisibility = if (currentVisibility == "전체공개") "비공개" else "전체공개"
            binding.btnVisibility.text = currentVisibility
        }

        // (2) [수정] 인스타그램식 하이라이트 토글 버튼
        binding.btnHighlightState.setOnClickListener {
            // 하이라이트 등록 상태 스위칭 (true <-> false)
            isHighlightedState = !isHighlightedState

            if (isHighlightedState) {
                applyCustomButtonState(binding.btnHighlightState, isSelected = true)
                Toast.makeText(requireContext(), "하이라이트에 등록하도록 설정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                applyCustomButtonState(binding.btnHighlightState, isSelected = false)
                Toast.makeText(requireContext(), "하이라이트 등록이 해제되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // (3) [최종 저장 버튼] - 이동 좌표(translationX/Y) 포함하여 DB에 물리 적재
        binding.btnSave.setOnClickListener {
            saveCurrentDiaryWithPosition()
        }

        initSingleChoiceGroups()
    }

    private fun applyEmotionButtonState(button: Button, isSelected: Boolean, originalColor: Int = 0) {
        if (button is MaterialButton) {
            val density = resources.displayMetrics.density
            if (isSelected) {
                button.backgroundTintList = ColorStateList.valueOf("#FFF59D".toColorInt())
                button.strokeColor = ColorStateList.valueOf("#000000".toColorInt())
                button.strokeWidth = (2 * density).toInt()
            } else {
                button.backgroundTintList = ColorStateList.valueOf(originalColor)
                button.strokeColor = ColorStateList.valueOf("#000000".toColorInt())
                button.strokeWidth = (1 * density).toInt()
            }
        }
    }

    private fun openEmotionPopup() {
        tempSelectedEmotions.clear()
        tempSelectedEmotions.addAll(selectedEmotions)

        emotionColorMap.forEach { (button, defaultColor) ->
            val emotionText = button.text.toString().replace("#", "")
            val isSelected = tempSelectedEmotions.contains(emotionText)
            applyCustomButtonState(button, isSelected = isSelected, originalColor = defaultColor)
        }
        binding.layoutEmotionPopup.visibility = View.VISIBLE
    }

    private fun initSingleChoiceGroups() {
        setupReminderChoiceGroup()

        // 2. End Date (Default: None)
        setupSingleChoiceGroup(
            listOf(binding.btnEndDateNone, binding.btnEndDateUser),
            defaultSelectedView = binding.btnEndDateNone
        )

        // 3. Visibility (Default: Public)
        setupSingleChoiceGroup(
            listOf(binding.btnVisibilityPublic, binding.btnVisibilityFriendOnly, binding.btnVisibilityPrivate),
            defaultSelectedView = binding.btnVisibilityPublic
        )
    }

    private fun setupReminderChoiceGroup() {
        val buttons = listOf(binding.btnRepeatAuto, binding.btnRepeatUser, binding.btnRepeatNone)

        fun select(selected: Button) {
            buttons.forEach { applyCustomButtonState(it, it == selected) }
        }

        select(binding.btnRepeatNone)
        binding.btnRepeatAuto.setOnClickListener {
            selectedReminderCycleDays = ReminderSchedulePolicy.AUTO_CURVE
            reminderSelectionTouched = true
            select(binding.btnRepeatAuto)
        }
        binding.btnRepeatNone.setOnClickListener {
            selectedReminderCycleDays = ReminderSchedulePolicy.DISABLED
            reminderSelectionTouched = true
            select(binding.btnRepeatNone)
        }
        binding.btnRepeatUser.setOnClickListener {
            val input = EditText(requireContext()).apply {
                hint = getString(R.string.reminder_custom_cycle_hint)
                inputType = InputType.TYPE_CLASS_NUMBER
                setPadding(48, 16, 48, 16)
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reminder_custom_cycle_title)
                .setView(input)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val days = input.text.toString().toIntOrNull()
                    if (days != null && days > 0) {
                        selectedReminderCycleDays = days
                        reminderSelectionTouched = true
                        select(binding.btnRepeatUser)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            R.string.reminder_custom_cycle_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .show()
        }
    }

    private fun resetPostItSettingUI() {
        currentSelectedColor = "yellow"
        selectedReminderCycleDays = ReminderSchedulePolicy.DISABLED
        reminderSelectionTouched = false
        selectedEmotions.clear()
        tempSelectedEmotions.clear()
        selectedTags.clear()

        // 1. 태그 상태 원복
        applyCustomButtonState(binding.chipTagDaily, isSelected = false)
        applyCustomButtonState(binding.chipTagWork, isSelected = false)
        binding.layoutDynamicTagsContainer.removeAllViews()

        // 2. 선택된 감정 컨테이너 초기화 및 '선택' 버튼 다시 표시
        binding.layoutSelectedEmotionsContainer.removeAllViews()
        binding.btnSelectEmotion.visibility = View.VISIBLE

        // 3. 반복, 종료일, 공개범위 단일 선택 버튼 그룹 기본값으로 리셋
        initSingleChoiceGroups()
    }

    // 설정창 내 선택된 감정 칩들 뿌리기
    private fun renderSelectedEmotionsInSetting() {
        binding.layoutSelectedEmotionsContainer.removeAllViews()

        if (selectedEmotions.isNotEmpty()) {
            for (emotion in selectedEmotions) {
                val emotionChip = TextView(requireContext()).apply {
                    text = getString(R.string.emotion_chip_format, emotion)
                    setBackgroundColor("#FFF59D".toColorInt())
                    setPadding(16, 4, 16, 4)
                    setTextColor(Color.BLACK)
                    textSize = 12f

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 8 }
                    layoutParams = params
                }
                binding.layoutSelectedEmotionsContainer.addView(emotionChip)
            }
            binding.btnSelectEmotion.visibility = View.GONE
        } else {
            binding.btnSelectEmotion.visibility = View.VISIBLE
        }
    }

    // 설정창에서 색상 클릭 시 현재 활성화된 포스트잇의 이미지 에셋을 교체하는 함수
    private fun updateActivePostItColor(colorName: String) {
        currentSelectedColor = colorName
        currentActivePostIt?.let { postIt ->
            postIt.tag = colorName
            val ivBg = postIt.findViewById<ImageView>(R.id.ivPostItBg)
            ivBg.setImageResource(postItResourceMap[colorName] ?: R.drawable.post_yellow)
        }
    }

    // 💡 뷰 터치 제어 오버라이딩을 활용하여 자유롭게 메모지를 드래그 이동시키는 유틸 메서드
    @SuppressLint("ClickableViewAccessibility")
    private fun makeViewDraggable(view: View) {
        var lastX = 0f
        var lastY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (v.tag != "DECORATION_TEXT") {
                        currentActivePostIt = v
                    }
                    // 터치 시작 시점의 손가락 좌표 기억
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    // 손가락이 이동한 거리 계산
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    // translationX, translationY를 직접 누적하여 옮긴 위치를 고정
                    v.translationX += dx
                    v.translationY += dy

                    // 다음 이동 거리 계산을 위해 기준점 갱신
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick()
                }
                else -> return@setOnTouchListener false
            }
            true
        }
    }

    private fun updateDateText() {
        binding.tvDateTitle.text = displayFormat.format(selectedDateCalendar.time)
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val targetCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }

                val todayStr = dateFormat.format(Date())
                val selectedStr = dateFormat.format(targetCal.time)

                if (selectedStr == todayStr) {
                    selectedDateCalendar = targetCal
                    updateDateText()
                    loadTodayDiary()
                } else {
                    // 과거 날짜 선택 시 상세 보기 전용 화면으로 데이터 넘기며 전환
                    val detailFragment = DiaryDetailFragment().apply {
                        arguments = Bundle().apply {
                            putString("TARGET_DATE", selectedStr)
                        }
                    }
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.main, detailFragment)
                        .addToBackStack(null)
                        .commit()
                }
            },
            selectedDateCalendar.get(Calendar.YEAR),
            selectedDateCalendar.get(Calendar.MONTH),
            selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun restoreTags(diary: DiaryEntity) {
        if (diary.tag.isNotBlank()) {
            val tags = diary.tag.split(",")
            for (rawTag in tags) {
                val tagName = rawTag.trim().replace("#", "")
                if (tagName.isBlank()) continue

                selectedTags.add(tagName)

                // XML에 이미 존재하는 기본 고정 태그면 하이라이트만 켜줌
                when (tagName) {
                    "일상" -> binding.chipTagDaily.setBackgroundColor("#FFF59D".toColorInt())
                    "업무" -> binding.chipTagWork.setBackgroundColor("#FFF59D".toColorInt())
                    else -> {
                        // 사용자 정의 태그만 동적으로 생성
                        addCustomTagChip(tagName)
                    }
                }
            }
        }
    }
    private fun loadTodayDiary() {
        val dateKey = dateFormat.format(selectedDateCalendar.time)
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val postIts = withContext(Dispatchers.IO) {
                db.diaryDao().getPostItsByDate(dateKey)
            }

            // 💡 [수정] 다이어리 컨테이너와 동적 태그 컨테이너 초기화
            binding.layoutDiaryContainer.removeAllViews()
            binding.layoutDynamicTagsContainer.removeAllViews()
            selectedTags.clear()

            applyCustomButtonState(binding.chipTagDaily, isSelected = false)
            applyCustomButtonState(binding.chipTagWork, isSelected = false)

            if (postIts.isEmpty()) {
                binding.tvEmptyHint.visibility = View.VISIBLE
            } else {
                binding.tvEmptyHint.visibility = View.GONE
                for (postIt in postIts) {
                    if (postIt.content.startsWith("[DECO]:")) {
                        // 꾸미기 텍스트 복원
                        val decText = postIt.content.replace("[DECO]:", "")
                        restoreDecorateTextView(decText, postIt.positionX, postIt.positionY, postIt.diaryId)
                    } else {
                        // 포스트잇 복원
                        inflateSavedPostIt(postIt)
                        restoreTags(postIt)
                    }
                }
            }
        }
    }

    private fun restoreDecorateTextView(textStr: String, posX: Float, posY: Float, diaryId: Int) {
        val decorateTextView = TextView(requireContext()).apply {
            text = textStr
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 8, 16, 8)
            tag = "DECORATION_TEXT"

            // 💡 불러온 좌표 고정 적용
            translationX = posX
            translationY = posY
            setTag(R.id.ivPostItBg, diaryId)

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        makeViewDraggable(decorateTextView)
        binding.layoutDiaryContainer.addView(decorateTextView)
        decorateTextView.bringToFront()
    }

    private fun inflateSavedPostIt(diary: DiaryEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val postItView = inflater.inflate(R.layout.item_diary_postit, binding.layoutDiaryContainer, false)

        val ivBg = postItView.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = postItView.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = postItView.findViewById<EditText>(R.id.etPostItContent)

        tvDate.text = diary.createdAt
        etContent.setText(diary.content)

        applyHighlightRangesToEditText(etContent, diary.highlightRanges)
        // 락 걸기 및 드래그 리스너 사전 부여 (기존 저장되어 로드된 항목이므로)
        setupPostItEditTextTouch(etContent, postItView)

        postItView.setTag(R.id.ivPostItBg, diary.diaryId) // diaryId 저장
        postItView.tag = diary.color                     // 색상 저장
        val colorKey = diary.color.lowercase(Locale.getDefault()).trim()
        val resId = postItResourceMap[colorKey] ?: R.drawable.post_yellow
        ivBg.setImageResource(resId)

        postItView.translationX = diary.positionX
        postItView.translationY = diary.positionY
        makeViewDraggable(postItView)

        binding.layoutDiaryContainer.addView(postItView)
    }

    private fun addNewPostItField(colorName: String = "yellow") {
        val inflater = LayoutInflater.from(requireContext())
        val postItView = inflater.inflate(R.layout.item_diary_postit, binding.layoutDiaryContainer, false)

        val ivBg = postItView.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = postItView.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = postItView.findViewById<EditText>(R.id.etPostItContent)

        tvDate.text = binding.tvDateTitle.text.toString()
        ivBg.setImageResource(postItResourceMap[colorName] ?: R.drawable.post_yellow)

        setupPostItEditTextTouch(etContent, postItView)
        postItView.tag = colorName
        makeViewDraggable(postItView)

        binding.layoutDiaryContainer.addView(postItView)
        postItView.bringToFront()
        binding.layoutDiaryContainer.bringToFront()

        currentActivePostIt = postItView // 제어 대상 지정

        binding.layoutPostItSetting.visibility = View.VISIBLE
        binding.layoutPostItSetting.bringToFront()
    }

    private fun saveCurrentDiaryWithPosition() {
        val dateKey = dateFormat.format(selectedDateCalendar.time)
        val tagsString = selectedTags.joinToString(",")
        val emotionsString = selectedEmotions.joinToString(",")

        val container = binding.layoutDiaryContainer
        val childCount = container.childCount

        if (childCount == 0 || (childCount == 1 && container.getChildAt(0) is TextView && container.getChildAt(0).id == R.id.tvEmptyHint)) {
            Toast.makeText(requireContext(), "저장할 메모지 내용이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 💡 [수정] 백그라운드 스레드에서 순차적으로 저장 후 ID 반영
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())

            for (i in 0 until childCount) {
                // 메인 스레드에서 UI 뷰 데이터 추출
                val childView = withContext(Dispatchers.Main) { container.getChildAt(i) }
                if (childView is TextView && childView.id == R.id.tvEmptyHint) continue

                if (childView is TextView && childView.tag == "DECORATION_TEXT") {
                    val decText = childView.text.toString().trim()
                    if (decText.isNotBlank()) {
                        val posX = childView.translationX
                        val posY = childView.translationY

                        val decDiaryEntity = DiaryEntity(
                            diaryId = (childView.getTag(R.id.ivPostItBg) as? Int) ?: 0,
                            createdAt = dateKey,
                            content = "[DECO]:$decText", // 꾸미기 텍스트 구분용 프리픽스
                            color = "transparent",
                            tag = "",
                            emotionStamp = "",
                            isHighlighted = false,
                            visibility = currentVisibility,
                            positionX = posX,
                            positionY = posY,
                            userId = 1,
                            highlightRanges = ""
                        )
                        val savedId = db.diaryDao().insertPostIt(decDiaryEntity)
                        withContext(Dispatchers.Main) {
                            childView.setTag(R.id.ivPostItBg, savedId.toInt())
                        }
                    }
                }
                // B. 일반 포스트잇일 경우
                else {
                    val etContent = childView.findViewById<EditText>(R.id.etPostItContent) ?: continue
                    val contentText = etContent.text.toString().trim()

                    if (contentText.isNotBlank()) {
                        val existingDiaryId = (childView.getTag(R.id.ivPostItBg) as? Int) ?: 0
                        val existingDiary = if (existingDiaryId > 0) {
                            db.diaryDao().getDiaryById(existingDiaryId)
                        } else null
                        val isReminderTarget = childView === currentActivePostIt
                        val shouldApplyReminderSelection = isReminderTarget &&
                            (existingDiary == null || reminderSelectionTouched)
                        val reminderCycle = if (shouldApplyReminderSelection) {
                            selectedReminderCycleDays
                        } else {
                            existingDiary?.reviewCycleDays ?: ReminderSchedulePolicy.DISABLED
                        }
                        val reminderWasChanged = shouldApplyReminderSelection &&
                            reminderCycle != existingDiary?.reviewCycleDays
                        val existingReminderAnchor = existingDiary?.reminderAnchorAt ?: 0L
                        val reminderAnchor = when {
                            reminderCycle == ReminderSchedulePolicy.DISABLED -> 0L
                            reminderWasChanged || existingReminderAnchor == 0L ->
                                System.currentTimeMillis()
                            else -> existingReminderAnchor
                        }
                        val reminderStage = if (reminderWasChanged) 0 else {
                            existingDiary?.reminderStage ?: 0
                        }
                        val savedEmotions = when {
                            childView !== currentActivePostIt ->
                                existingDiary?.emotionStamp.orEmpty()
                            emotionsString.isNotBlank() || existingDiary == null ->
                                emotionsString
                            else -> existingDiary.emotionStamp.orEmpty()
                        }
                        val postItColor = (childView.tag as? String) ?: currentSelectedColor
                        val posX = childView.translationX
                        val posY = childView.translationY

                        val highlightRanges = withContext(Dispatchers.Main) {
                            getHighlightRangesFromEditText(etContent)
                        }

                        val newDiary = DiaryEntity(
                            diaryId = existingDiaryId,
                            createdAt = dateKey,
                            content = contentText,
                            color = postItColor,
                            tag = tagsString,
                            emotionStamp = savedEmotions,
                            isHighlighted = isHighlightedState,
                            visibility = currentVisibility,
                            reviewCycleDays = reminderCycle,
                            lastRemindedAt = if (reminderWasChanged) 0L else {
                                existingDiary?.lastRemindedAt ?: 0L
                            },
                            reminderAnchorAt = reminderAnchor,
                            reminderStage = reminderStage,
                            positionX = posX,
                            positionY = posY,
                            userId = 1,
                            highlightRanges = highlightRanges
                        )

                        val savedId = db.diaryDao().insertPostIt(newDiary)
                        val savedDiary = newDiary.copy(diaryId = savedId.toInt())
                        if (reminderCycle == ReminderSchedulePolicy.DISABLED) {
                            ReminderScheduler.cancel(requireContext(), savedId.toInt())
                        } else {
                            ReminderScheduler.schedule(requireContext(), savedDiary)
                        }

                        withContext(Dispatchers.Main) {
                            childView.setTag(R.id.ivPostItBg, savedId.toInt())
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "저장되었습니다!", Toast.LENGTH_SHORT).show()
                binding.layoutPostItSetting.visibility = View.GONE
                selectedEmotions.clear()
            }
        }
    }
    private fun showAddTagDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.dialog_add_tag_title))

        val input = EditText(requireContext()).apply {
            hint = getString(R.string.dialog_add_tag_hint)
            setSingleLine()
        }

        // 입력창 여백(Padding) 세팅
        val container = android.widget.FrameLayout(requireContext()).apply {
            val params = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = 50
            params.rightMargin = 50
            layoutParams = params
            addView(input)
        }
        builder.setView(container)

        builder.setPositiveButton(getString(R.string.action_add)) { dialog, _ ->
            val newTagText = input.text.toString().trim().replace("#", "")
            if (newTagText.isNotBlank()) {
                addCustomTagChip(newTagText)
            } else {
                Toast.makeText(requireContext(), "태그 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton(getString(R.string.action_cancel)) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun addCustomTagChip(tagName: String) {
        val density = resources.displayMetrics.density
        val heightInPx = (32 * density).toInt()
        val marginEndInPx = (8 * density).toInt()
        val paddingPx = (10 * density).toInt()

        val newTagBtn = MaterialButton(requireContext()).apply {
            text = getString(R.string.emotion_chip_format, tagName)
            textSize = 12f
            setTextColor(Color.BLACK)

            setPadding(paddingPx, 0, paddingPx, 0)

            minHeight = 0
            insetTop = 0
            insetBottom = 0

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightInPx
            ).apply {
                marginEnd = marginEndInPx
            }
            layoutParams = params

            applyCustomButtonState(this, isSelected = true)

            // 선택 클릭 피드백 적용
            setOnClickListener {
                if (selectedTags.contains(tagName)) {
                    selectedTags.remove(tagName)
                    // 해제 시 투명 배경으로 안전하게 원상복구
                    applyCustomButtonState(this, isSelected = false)
                } else {
                    selectedTags.add(tagName)
                    applyCustomButtonState(this, isSelected = true)
                }
            }
        }

        binding.layoutDynamicTagsContainer.addView(newTagBtn)
    }

    private fun applyHighlightToSelectedText() {
        val activeView = currentActivePostIt
        if (activeView == null) {
            Toast.makeText(requireContext(), "형광펜을 칠할 포스트잇을 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val etContent = activeView.findViewById<EditText>(R.id.etPostItContent)
        if (etContent == null) {
            Toast.makeText(requireContext(), "포스트잇 내 텍스트 영역을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val start = etContent.selectionStart
        val end = etContent.selectionEnd

        // 블록 지정이 되어있는지 확인 (start와 end가 같으면 블록 지정 안 됨)
        if (start < 0 || end < 0 || start == end) {
            Toast.makeText(requireContext(), "형광펜을 칠할 텍스트 영역을 드래그하여 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val editableText = etContent.text
        val highlightedPart = editableText.substring(start, end)

        // 텍스트에 형광펜 배경색(노란색) Spannable 적용
        val spannable = if (editableText is Spannable) editableText else SpannableString(editableText)
        spannable.setSpan(
            BackgroundColorSpan("#FFF59D".toColorInt()),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        etContent.setText(spannable)
        etContent.setSelection(end)

        // 선택된 날짜 및 텍스트 DB 적재
        val dateKey = dateFormat.format(selectedDateCalendar.time)
        val existingDiaryId = (activeView.getTag(R.id.ivPostItBg) as? Int) ?: 0

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.diaryDao().insertHighlight(
                HighlightEntity(
                    diaryId = existingDiaryId,
                    date = dateKey,
                    highlightedText = highlightedPart
                )
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "'${highlightedPart}' 형광펜 텍스트가 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================================================================
    // 📝 3. 텍스트 추가 기능: 일기장 컨테이너 자체에 꾸미기용 텍스트 추가
    // =========================================================================
    private fun showAddDirectTextDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("일기장에 텍스트 추가")

        val input = EditText(requireContext()).apply {
            hint = "일기장을 꾸밀 문구를 입력하세요"
            setSingleLine()
        }

        val container = android.widget.FrameLayout(requireContext()).apply {
            val params = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = 50
            params.rightMargin = 50
            layoutParams = params
            addView(input)
        }
        builder.setView(container)

        builder.setPositiveButton("추가") { dialog, _ ->
            val textContent = input.text.toString().trim()
            if (textContent.isNotBlank()) {
                addDecorateTextViewToContainer(textContent)
            } else {
                Toast.makeText(requireContext(), "내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    // 일기장 컨테이너에 자유 배치 텍스트 뷰 꽂아넣기
    private fun addDecorateTextViewToContainer(textStr: String) {
        val decorateTextView = TextView(requireContext()).apply {
            text = textStr
            textSize = 16f
            setTextColor(Color.BLACK)
            // 배경을 살짝 투명하게 하거나 깔끔하게 처리
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 8, 16, 8)
            tag = "DECORATION_TEXT"

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        // 자유 이동 드래그 리스너 할당
        makeViewDraggable(decorateTextView)

        // 컨테이너에 뷰 추가 및 맨 앞으로 노출
        binding.layoutDiaryContainer.addView(decorateTextView)
        decorateTextView.bringToFront()

        Toast.makeText(requireContext(), "텍스트가 추가되었습니다. 원하는 위치로 드래그해 보세요!", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPostItEditTextTouch(etContent: EditText, postItView: View) {
        etContent.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 이 포스트잇을 현재 활성화 포스트잇으로 지정 (형광펜 대상)
                    currentActivePostIt = postItView
                    // 상위 드래그 리스너가 텍스트 드래그 이벤트를 가로채지 못하도록 방어
                    etContent.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    etContent.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false // false를 리턴해야 EditText 본연의 텍스트 드래그/커서 지정 동작이 수행됨
        }
    }

    // 💡 EditText에 적용된 형광펜 스팬들의 (시작-끝) 범위를 "0-5,10-15" 형태의 문자열로 변환
    private fun getHighlightRangesFromEditText(etContent: EditText): String {
        val spannable = etContent.text as? Spannable ?: return ""
        val spans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
        val rangeList = mutableListOf<String>()

        for (span in spans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start in 0..<end) {
                rangeList.add("$start-$end")
            }
        }
        return rangeList.joinToString(",")
    }

    // 💡 저장된 "0-5,10-15" 문자열 정보를 읽어와 EditText 텍스트에 노란색 형광펜 적용
    private fun applyHighlightRangesToEditText(etContent: EditText, rangesStr: String) {
        if (rangesStr.isBlank()) return

        val text = etContent.text.toString()
        val spannable = SpannableString(text)
        val pairs = rangesStr.split(",")

        for (pair in pairs) {
            val parts = pair.split("-")
            if (parts.size == 2) {
                val start = parts[0].toIntOrNull() ?: continue
                val end = parts[1].toIntOrNull() ?: continue

                if (start in 0..text.length && end in start..text.length) {
                    spannable.setSpan(
                        BackgroundColorSpan("#FFF59D".toColorInt()),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        etContent.setText(spannable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL_DATE = "initial_diary_date"

        fun newInstance(dateKey: String) = DiaryFragment().apply {
            arguments = Bundle().apply { putString(ARG_INITIAL_DATE, dateKey) }
        }
    }
}
