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
import androidx.core.content.ContextCompat
import android.util.TypedValue
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
    private var tempSelectedEmotion: String = "" // 팝업창에서 임시 선택한 감정 텍스트
    private val selectedEmotions = mutableListOf<String>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("M월 d일", Locale.getDefault())
    private var selectedDateCalendar = Calendar.getInstance()

    private fun setupSingleChoiceGroup(buttons: List<TextView>, defaultSelectedIndex: Int = 0) {
        // 1. 각 버튼의 초기 XML 원본 배경을 tag에 보관
        buttons.forEach { btn ->
            btn.tag = btn.background
        }

        // 2. 초기 선택값 및 클릭 이벤트 처리
        buttons.forEachIndexed { index, textView ->
            if (index == defaultSelectedIndex) {
                textView.setBackgroundColor("#FFF59D".toColorInt()) // 선택 항목 하이라이트
            } else {
                textView.background = textView.tag as? android.graphics.drawable.Drawable // 원래 XML 배경으로 복원
            }

            textView.setOnClickListener {
                // 모든 버튼을 각자의 원래 XML 배경으로 복원
                buttons.forEach { btn ->
                    btn.background = btn.tag as? android.graphics.drawable.Drawable
                }
                // 눌린 버튼만 하이라이트 색상 적용
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
            tempSelectedEmotion = ""
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
                tempSelectedEmotion = v.text.toString().replace("#", "")
                // 선택한 칩에 투명 노란색 피드백 유도 (나중에 원하는 드로어블로 대체 가능)
                v.setBackgroundColor("#FFF59D".toColorInt())
            }
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
            if (tempSelectedEmotion.isNotBlank()) {
                if (!selectedEmotions.contains(tempSelectedEmotion)) {
                    selectedEmotions.add(tempSelectedEmotion)
                }
                binding.layoutSelectedEmotionsContainer.removeAllViews()

                for (emotion in selectedEmotions) {
                    val emotionChip = TextView(requireContext()).apply {
                        text = getString(R.string.emotion_chip_format, emotion)
                        setBackgroundColor("#FFF59D".toColorInt())
                        setPadding(16, 4, 16, 4)
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 12f

                        // 마진 추가
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = 8 }
                        layoutParams = params
                    }
                    binding.layoutSelectedEmotionsContainer.addView(emotionChip)
                }
                binding.btnSelectEmotion.visibility = View.GONE // 감정 선택 완료 시 선택 버튼 숨김
            }
        }
        val tagSelectListener = View.OnClickListener { v ->
            if (v is TextView) {
                val tagName = v.text.toString().replace("#", "")
                if (selectedTags.contains(tagName)) {
                    selectedTags.remove(tagName)
                    // 선택 해제 시: 저장해둔 원래 배경으로 복원 (없으면 기본값)
                    v.background = v.tag as? android.graphics.drawable.Drawable ?: ContextCompat.getDrawable(requireContext(), R.drawable.bg_comment_btn)
                } else {
                    selectedTags.add(tagName)
                    // 선택 시: 노란색 강조
                    v.setBackgroundColor("#FFF59D".toColorInt())
                }
            }
        }

// 태그 버튼들의 원본 배경을 tag에 백업 후 리스너 등록
        binding.chipTagDaily.tag = binding.chipTagDaily.background
        binding.chipTagDaily.setOnClickListener(tagSelectListener)

        binding.chipTagWork.tag = binding.chipTagWork.background
        binding.chipTagWork.setOnClickListener(tagSelectListener)

        // 💡 [신규] + 버튼 클릭 시 새 태그 입력 대화상자(AlertDialog) 띄우기
        binding.btnAddCustomTag.setOnClickListener {
            showAddTagDialog()
        }

        // 1. 반복주기 그룹 선택 반응 설정 (기본값: 안함)
        setupSingleChoiceGroup(
            listOf(binding.btnRepeatAuto, binding.btnRepeatUser, binding.btnRepeatNone),
            defaultSelectedIndex = 2
        )

        // 2. 종료일자 그룹 선택 반응 설정 (기본값: 안함)
        setupSingleChoiceGroup(
            listOf(binding.btnEndDateNone, binding.btnEndDateUser),
            defaultSelectedIndex = 0
        )

        // 3. 공개범위 그룹 선택 반응 설정 (기본값: 전체공개)
        setupSingleChoiceGroup(
            listOf(binding.btnVisibilityPublic, binding.btnVisibilityFriendOnly, binding.btnVisibilityPrivate),
            defaultSelectedIndex = 0
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

                    // DB 최종 저장 처리 호출
                    saveCurrentDiary(contentText, currentSelectedColor, tempSelectedEmotion)

                    // 메모지 위치를 자유롭게 옮길 수 있도록 드래그 리스너 부착
                    makeViewDraggable(postIt)

                    binding.layoutPostItSetting.visibility = View.GONE
                    currentActivePostIt = null
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

        // 6. 단일 선택 그룹 리스너 설정
        setupSingleChoiceGroup(
            listOf(binding.btnRepeatAuto, binding.btnRepeatUser, binding.btnRepeatNone),
            defaultSelectedIndex = 2
        )
        setupSingleChoiceGroup(
            listOf(binding.btnEndDateNone, binding.btnEndDateUser),
            defaultSelectedIndex = 0
        )
        setupSingleChoiceGroup(
            listOf(binding.btnVisibilityPublic, binding.btnVisibilityFriendOnly, binding.btnVisibilityPrivate),
            defaultSelectedIndex = 0
        )
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
            for (tagName in tags) {
                addCustomTagChip(tagName) // 기존 화면 구성 함수 재활용
                selectedTags.add(tagName) // 상태 복원
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
            binding.layoutDiaryContainer.removeAllViews()
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

    // 💡 [구현 완료] 설정 완료 시 비동기로 Room DB에 물리 적재하는 핵심 로직
    private fun saveCurrentDiary(content: String, color: String, emotion: String) {
        val dateKey = dateFormat.format(selectedDateCalendar.time)

        // 선택된 태그 세트를 "일상,업무,운동" 형태의 문자열로 변환
        val tagsString = selectedTags.joinToString(",")

        val newDiary = DiaryEntity(
            createdAt = dateKey,
            content = content,
            color = color,
            emotionStamp = emotion,
            tag = tagsString, // 💡 DiaryEntity의 tag 컬럼에 직접 저장
            userId = 1
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.diaryDao().insertPostIt(newDiary)

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "기록이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun saveCurrentDiaryWithPosition() {
        val dateKey = dateFormat.format(selectedDateCalendar.time)
        val tagsString = selectedTags.joinToString(",")

        val emotionsString = selectedEmotions.joinToString(",")
        // 현재 작업 중인 활성 포스트잇이 있거나 컨테이너에 배치된 뷰가 있는지 확인
        val targetView = currentActivePostIt ?: binding.layoutDiaryContainer.getChildAt(0)

        if (targetView == null) {
            Toast.makeText(requireContext(), "저장할 메모지 내용이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val etContent = targetView.findViewById<EditText>(R.id.etPostItContent)
        val contentText = etContent.text.toString().trim()

        if (contentText.isBlank()) {
            Toast.makeText(requireContext(), "내용을 입력해야 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val existingDiaryId = (targetView.getTag(R.id.ivPostItBg) as? Int) ?: 0
        val postItColor = (targetView.tag as? String) ?: currentSelectedColor

        // 💡 이동된 좌표값(translationX, translationY) 추출
        val posX = targetView.translationX
        val posY = targetView.translationY

        val newDiary = DiaryEntity(
            diaryId = existingDiaryId,
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

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            // DB에 저장 (OnConflictStrategy.REPLACE에 의해 diaryId가 같으면 덮어씌워짐!)
            val savedId = db.diaryDao().insertPostIt(newDiary)

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "저장되었습니다!", Toast.LENGTH_SHORT).show()

                // 💡 [핵심 3] 신규 생성된 ID를 뷰에 다시 기록하여 다음 저장 때 복제 방지
                targetView.setTag(R.id.ivPostItBg, savedId.toInt())

                etContent.isEnabled = false
                etContent.clearFocus()
                makeViewDraggable(targetView)
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

    // 💡 텍스트를 받아 동적으로 태그 TextView 칩을 만들어 컨테이너에 추가하는 메서드
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
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                } else {
                    selectedTags.add(tagName)
                    setBackgroundColor("#FFF59D".toColorInt()) // 선택 색상
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