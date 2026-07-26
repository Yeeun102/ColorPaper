package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentDiaryBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
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

    private var emotionResMap: Map<TextView, Int> = emptyMap()

    private fun setupSingleChoiceGroup(
        buttonResMap: Map<TextView, Int>,
        defaultSelectedView: TextView
    ) {
        val buttons = buttonResMap.keys.toList()

        buttons.forEach { textView ->
            val resId = buttonResMap[textView] ?: 0

            // 초기 세팅
            if (textView == defaultSelectedView) {
                textView.setBackgroundColor("#FFF59D".toColorInt())
            } else {
                if (resId != 0) textView.setBackgroundResource(resId) else textView.background = null
            }

            // 클릭 시 이벤트
            textView.setOnClickListener {
                buttons.forEach { btn ->
                    val btnResId = buttonResMap[btn] ?: 0
                    if (btnResId != 0) {
                        btn.setBackgroundResource(btnResId)
                    } else {
                        btn.background = null
                    }
                }
                textView.setBackgroundColor("#FFF59D".toColorInt())
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

        emotionResMap = mapOf(
            binding.emo1 to R.drawable.bg_happy,
            binding.emo2 to R.drawable.bg_excited,
            binding.emo3 to R.drawable.bg_satisfied,
            binding.emo4 to R.drawable.bg_relaxed,
            binding.emo5 to R.drawable.bg_annoyed,
            binding.emo6 to R.drawable.bg_exhausted,
            binding.emo7 to R.drawable.bg_angry,
            binding.emo8 to R.drawable.bg_sleepy,
            binding.emo9 to R.drawable.bg_depressed,
            binding.emo10 to R.drawable.bg_upset,
            binding.emo11 to R.drawable.bg_anxious,
            binding.emo12 to R.drawable.bg_sad
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

        // 2. 설정창 내부 색상 서클 클릭 시 -> 실시간 메모지 SVG 파일 교체
        binding.viewColorOrange.setOnClickListener { updateActivePostItColor("orange") }
        binding.viewColorYellow.setOnClickListener { updateActivePostItColor("yellow") }
        binding.viewColorGreen.setOnClickListener  { updateActivePostItColor("green") }
        binding.viewColorBlue.setOnClickListener   { updateActivePostItColor("blue") }

        // 3. 설정창 내부에서 '감정 선택하기' 클릭 시 팝업 활성화
        binding.btnSelectEmotion.setOnClickListener {
            binding.layoutEmotionPopup.visibility = View.VISIBLE
        }

        // 4. 팝업 내부의 감정 칩 리스너 매핑 (#기뻐요 예시 및 기타 감정 확장 구조)
        val emotionClicker = View.OnClickListener { v ->
            if (v is TextView) {
                val emotionText = v.text.toString().replace("#", "")
                val origResId = emotionResMap[v] ?: 0

                if (tempSelectedEmotions.contains(emotionText)) {
                    tempSelectedEmotions.remove(emotionText)
                    if (origResId != 0) v.setBackgroundResource(origResId) else v.background = null
                } else {
                    tempSelectedEmotions.add(emotionText)
                    v.setBackgroundColor("#FFF59D".toColorInt()) // 선택 피드백
                }
            }
        }
        emotionResMap.keys.forEach { textView ->
            textView.setOnClickListener(emotionClicker)
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
            if (v is TextView) {
                val tagName = v.text.toString().replace("#", "")
                if (selectedTags.contains(tagName)) {
                    selectedTags.remove(tagName)
                    v.background = null // 해제 시 투명 배경으로 복원
                } else {
                    selectedTags.add(tagName)
                    v.setBackgroundColor("#FFF59D".toColorInt())
                }
            }
        }
        binding.chipTagDaily.setOnClickListener(tagSelectListener)
        binding.chipTagWork.setOnClickListener(tagSelectListener)

        binding.btnAddCustomTag.setOnClickListener {
            showAddTagDialog()
        }

        // 1. 반복주기 그룹 선택 반응 설정 (기본값: 안함)
        setupSingleChoiceGroup(
            mapOf(
                binding.btnRepeatAuto to R.drawable.bg_repeatauto,
                binding.btnRepeatUser to R.drawable.bg_repeatuser,
                binding.btnRepeatNone to R.drawable.bg_repeatnone
            ),
            defaultSelectedView = binding.btnRepeatNone
        )

        // 2. 종료일자 그룹 선택 반응 설정 (기본값: 안함)
        setupSingleChoiceGroup(
            mapOf(
                binding.btnEndDateNone to R.drawable.bg_repeatnone,
                binding.btnEndDateUser to R.drawable.bg_repeatuser
            ),
            defaultSelectedView = binding.btnEndDateNone
        )

        // 3. 공개범위 그룹 선택 반응 설정 (기본값: 전체공개)
        setupSingleChoiceGroup(
            mapOf(
                binding.btnVisibilityPublic to R.drawable.bg_public,
                binding.btnVisibilityFriendOnly to R.drawable.bg_friendonly,
                binding.btnVisibilityPrivate to R.drawable.bg_private
            ),
            defaultSelectedView = binding.btnVisibilityPublic
        )

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
        /*
        // (1) 공개여부 토글 버튼
        binding.btnVisibility.setOnClickListener {
            currentVisibility = if (currentVisibility == "전체공개") "비공개" else "전체공개"
            binding.btnVisibility.text = currentVisibility
        }
        */

        // (2) [수정] 인스타그램식 하이라이트 토글 버튼
        binding.btnHighlightState.setOnClickListener {
            // 하이라이트 등록 상태 스위칭 (true <-> false)
            isHighlightedState = !isHighlightedState

            if (isHighlightedState) {
                // 선택 시 시각적 하이라이트 피드백 (노란색 강조)
                binding.btnHighlightState.setBackgroundColor("#FFF59D".toColorInt())
                Toast.makeText(requireContext(), "하이라이트에 등록하도록 설정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                // 해제 시 투명 배경으로 복원
                binding.btnHighlightState.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                Toast.makeText(requireContext(), "하이라이트 등록이 해제되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // (3) [최종 저장 버튼] - 이동 좌표(translationX/Y) 포함하여 DB에 물리 적재
        binding.btnSave.setOnClickListener {
            saveCurrentDiaryWithPosition()
        }

        initSingleChoiceGroups()
    }

    private fun initSingleChoiceGroups() {
        setupSingleChoiceGroup(
            mapOf(
                binding.btnRepeatAuto to R.drawable.bg_repeatauto,
                binding.btnRepeatUser to R.drawable.bg_repeatuser,
                binding.btnRepeatNone to R.drawable.bg_repeatnone
            ),
            defaultSelectedView = binding.btnRepeatNone
        )

        setupSingleChoiceGroup(
            mapOf(
                binding.btnEndDateNone to R.drawable.bg_repeatnone,
                binding.btnEndDateUser to R.drawable.bg_repeatuser
            ),
            defaultSelectedView = binding.btnEndDateNone
        )

        setupSingleChoiceGroup(
            mapOf(
                binding.btnVisibilityPublic to R.drawable.bg_public,
                binding.btnVisibilityFriendOnly to R.drawable.bg_friendonly,
                binding.btnVisibilityPrivate to R.drawable.bg_private
            ),
            defaultSelectedView = binding.btnVisibilityPublic
        )
    }

    private fun resetPostItSettingUI() {
        currentSelectedColor = "yellow"
        selectedEmotions.clear()
        tempSelectedEmotions.clear()
        selectedTags.clear()

        // 1. 태그 상태 원복
        binding.chipTagDaily.background = null
        binding.chipTagWork.background = null
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
                    setTextColor(android.graphics.Color.BLACK)
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

            if (postIts.isEmpty()) {
                binding.tvEmptyHint.visibility = View.VISIBLE
            } else {
                binding.tvEmptyHint.visibility = View.GONE
                for (postIt in postIts) {
                    inflateSavedPostIt(postIt)
                    restoreTags(postIt)
                }
            }
        }
    }

    private fun inflateSavedPostIt(diary: DiaryEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val postItView = inflater.inflate(R.layout.item_diary_postit, binding.layoutDiaryContainer, false)

        val ivBg = postItView.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = postItView.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = postItView.findViewById<EditText>(R.id.etPostItContent)

        tvDate.text = diary.createdAt
        etContent.setText(diary.content)

        // 락 걸기 및 드래그 리스너 사전 부여 (기존 저장되어 로드된 항목이므로)
        etContent.isEnabled = false

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

        // 💡 고정 더미 대신 사용자가 선택해둔 상단 바의 실제 텍스트 값 맵핑
        tvDate.text = binding.tvDateTitle.text.toString()
        ivBg.setImageResource(postItResourceMap[colorName] ?: R.drawable.post_yellow)

        postItView.tag = colorName

        binding.layoutDiaryContainer.addView(postItView)
        currentActivePostIt = postItView // 제어 대상 지정

        binding.layoutPostItSetting.visibility = View.VISIBLE
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

                val etContent = childView.findViewById<EditText>(R.id.etPostItContent) ?: continue
                val contentText = etContent.text.toString().trim()

                if (contentText.isNotBlank()) {
                    val existingDiaryId = (childView.getTag(R.id.ivPostItBg) as? Int) ?: 0
                    val postItColor = (childView.tag as? String) ?: currentSelectedColor
                    val posX = childView.translationX
                    val posY = childView.translationY

                    val newDiary = DiaryEntity(
                        diaryId = existingDiaryId, // 0이면 INSERT, 기존 ID면 UPDATE
                        createdAt = dateKey,
                        content = contentText,
                        color = postItColor,
                        tag = tagsString,
                        emotionStamp = emotionsString,
                        isHighlighted = isHighlightedState,
                        visibility = currentVisibility,
                        positionX = posX,
                        positionY = posY,
                        userId = 1
                    )

                    // DB 저장 수행 후 발급된 ID 세팅
                    val savedId = db.diaryDao().insertPostIt(newDiary)

                    withContext(Dispatchers.Main) {
                        childView.setTag(R.id.ivPostItBg, savedId.toInt())
                        etContent.isEnabled = false
                        etContent.clearFocus()
                        makeViewDraggable(childView)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "저장되었습니다!", Toast.LENGTH_SHORT).show()
                binding.layoutPostItSetting.visibility = View.GONE
                currentActivePostIt = null
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
        val heightInPx = (26 * density).toInt()
        val marginEndInPx = (8 * density).toInt()

        val newTagChip = TextView(requireContext()).apply {
            text = getString(R.string.emotion_chip_format, tagName)
            gravity = android.view.Gravity.CENTER
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.BLACK)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightInPx
            ).apply {
                marginEnd = marginEndInPx
            }
            layoutParams = params

            // 선택 클릭 피드백 적용
            setOnClickListener {
                if (selectedTags.contains(tagName)) {
                    selectedTags.remove(tagName)
                    // 해제 시 투명 배경으로 안전하게 원상복구
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                } else {
                    selectedTags.add(tagName)
                    setBackgroundColor("#FFF59D".toColorInt())
                }
            }
        }

        binding.layoutDynamicTagsContainer.addView(newTagChip)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}