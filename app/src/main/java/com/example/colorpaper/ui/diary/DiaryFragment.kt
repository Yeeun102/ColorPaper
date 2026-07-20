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
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentDiaryBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryPostIt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

class DiaryFragment : Fragment() {
    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!

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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("M월 d일", Locale.getDefault())
    private var selectedDateCalendar = Calendar.getInstance()

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
                val emotionChip = TextView(requireContext()).apply {
                    text = getString(R.string.emotion_chip_format, tempSelectedEmotion)
                    setBackgroundResource(R.drawable.bg_comment_btn)
                    setPadding(12, 6, 12, 6)
                    setTextColor(android.graphics.Color.BLACK)
                    textSize = 12f
                }
                binding.layoutSelectedEmotionsContainer.removeAllViews()
                binding.layoutSelectedEmotionsContainer.addView(emotionChip)
                binding.btnSelectEmotion.visibility = View.GONE // 감정 선택 완료 시 선택 버튼 숨김
            }
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
    }

    // 설정창에서 색상 클릭 시 현재 활성화된 포스트잇의 이미지 에셋을 교체하는 함수
    private fun updateActivePostItColor(colorName: String) {
        currentSelectedColor = colorName
        currentActivePostIt?.let { postIt ->
            val ivBg = postIt.findViewById<ImageView>(R.id.ivPostItBg)
            ivBg.setImageResource(postItResourceMap[colorName] ?: R.drawable.post_yellow)
        }
    }

    // 💡 뷰 터치 제어 오버라이딩을 활용하여 자유롭게 메모지를 드래그 이동시키는 유틸 메서드
    @SuppressLint("ClickableViewAccessibility")
    private fun makeViewDraggable(view: View) {
        var dX = 0f
        var dY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
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
                }
            }
        }
    }

    private fun inflateSavedPostIt(postIt: DiaryPostIt) {
        val inflater = LayoutInflater.from(requireContext())
        val postItView = inflater.inflate(R.layout.item_diary_postit, binding.layoutDiaryContainer, false)

        val ivBg = postItView.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = postItView.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = postItView.findViewById<EditText>(R.id.etPostItContent)

        tvDate.text = postIt.date
        etContent.setText(postIt.content)

        // 락 걸기 및 드래그 리스너 사전 부여 (기존 저장되어 로드된 항목이므로)
        etContent.isEnabled = false
        makeViewDraggable(postItView)

        val resId = postItResourceMap[postIt.color] ?: R.drawable.post_yellow
        ivBg.setImageResource(resId)

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

        binding.layoutDiaryContainer.addView(postItView)
        currentActivePostIt = postItView // 제어 대상 지정

        binding.layoutPostItSetting.visibility = View.VISIBLE
    }

    // 💡 [구현 완료] 설정 완료 시 비동기로 Room DB에 물리 적재하는 핵심 로직
    private fun saveCurrentDiary(content: String, color: String, emotion: String) {
        val dateKey = dateFormat.format(selectedDateCalendar.time)

        val newPostIt = DiaryPostIt(
            date = dateKey,
            content = content,
            color = color,
            emotion = emotion
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.diaryDao().insertPostIt(newPostIt)

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "기록이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}