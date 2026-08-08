package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.graphics.toColorInt
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentDiaryDetailBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.util.AuthUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DiaryDetailFragment : Fragment() {
    private var _binding: FragmentDiaryDetailBinding? = null
    private val binding get() = _binding!!

    // 일기 본문용 리소스 맵
    private val postItResourceMap = mapOf(
        "orange" to R.drawable.post_orange,
        "yellow" to R.drawable.post_yellow,
        "green"  to R.drawable.post_green,
        "blue"   to R.drawable.post_blue
    )

    // 댓글용 전용 리소스 맵
    private val commentResourceMap = mapOf(
        "orange" to R.drawable.comment_orange,
        "yellow" to R.drawable.comment_yellow,
        "green"  to R.drawable.comment_green,
        "blue"   to R.drawable.comment_blue
    )

    private var targetDate: String = ""
    private var currentVisibility: String = "전체공개"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var buttonColorMap: Map<Button, Int> = emptyMap()

    private var isPastHighlighted = false



    private fun applyCustomButtonState(button: Button, isSelected: Boolean, originalColor: Int = 0) {
        if (button is MaterialButton) {
            val density = resources.displayMetrics.density
            val defaultColor = if (originalColor != 0) originalColor else (buttonColorMap[button] ?: "#E4D0D0".toColorInt())

            if (isSelected) {
                button.backgroundTintList = ColorStateList.valueOf("#FFF59D".toColorInt())
                button.strokeColor = ColorStateList.valueOf("#000000".toColorInt())
                button.strokeWidth = (2 * density).toInt()
            } else {
                button.backgroundTintList = ColorStateList.valueOf(defaultColor)
                button.strokeColor = ColorStateList.valueOf("#000000".toColorInt())
                button.strokeWidth = (1 * density).toInt()
            }
            button.invalidate()
            button.refreshDrawableState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetDate = arguments?.getString("TARGET_DATE") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonColorMap = mapOf(
            binding.btnVisibilityDetail to "#E4D0D0".toColorInt(),
            binding.btnSaveDetail to "#867070".toColorInt(),
            binding.btnHighlightDetail to "#D5B4B4".toColorInt()
        )

        updateTitleDateText()

        binding.btnDatePickerDetail.setOnClickListener {
            showDatePicker()
        }

        // 과거 기록이므로 편집 기능(추가 버튼, 저장 버튼) 제거 및 숨김 규칙 적용
        binding.btnSaveDetail.visibility = View.VISIBLE

        binding.btnToolbarAddDetail.isEnabled = false
        binding.btnToolbarAddDetail.alpha = 0.3f

        // 기존 일기 및 저장된 댓글 로드
        loadDiaryAndComments()

        binding.btnSaveDetail.setOnClickListener {
            //saveAllCommentsPositions()
            saveAllCommentsAndDiaryState()
        }

        binding.btnVisibilityDetail.setOnClickListener {
            if (currentVisibility == "전체공개") {
                currentVisibility = "비공개"
                binding.btnVisibilityDetail.text = getString(R.string.flashcard_private)
                applyCustomButtonState(binding.btnVisibilityDetail, isSelected = true)
            } else {
                currentVisibility = "전체공개"
                binding.btnVisibilityDetail.text = getString(R.string.flashcard_public)
                applyCustomButtonState(binding.btnVisibilityDetail, isSelected = false)
            }
        }

        binding.btnHighlightDetail.setOnClickListener {
            val currentUid = AuthUtils.getCurrentUserId()

            // 저장 버튼을 누르지 않아도 DB에 즉시 반영
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                val postIts = db.diaryDao().getPostItsByDateAndUserId(targetDate, currentUid)

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    if (postIts.isEmpty()){
                        Toast.makeText(requireContext(), "하이라이트에 등록할 다이어리가 없습니다.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    isPastHighlighted = !isPastHighlighted
                    applyCustomButtonState(binding.btnHighlightDetail, isSelected = isPastHighlighted)

                    // DB 일괄 업데이트
                    lifecycleScope.launch(Dispatchers.IO) {
                        for (postIt in postIts) {
                            val updatedPostIt = postIt.copy(isHighlighted = isPastHighlighted)
                            db.diaryDao().insertPostIt(updatedPostIt)
                        }

                        withContext(Dispatchers.Main) {
                            if (!isAdded || _binding == null) return@withContext
                            val msg = if (isPastHighlighted) "하이라이트에 등록되었습니다." else "하이라이트 등록이 해제되었습니다."
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        val todayStr = dateFormat.format(Date())
        val isFutureDate = targetDate > todayStr

        if (isFutureDate) {
            binding.btnToolbarCommentDetail.alpha = 0.3f
        } else {
            binding.btnToolbarCommentDetail.alpha = 1.0f
        }

        binding.btnToolbarCommentDetail.setOnClickListener {
            if (isFutureDate) {
                Toast.makeText(requireContext(), "미래의 일기에는 댓글을 작성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 1. 4가지 색상 파일 중 하나를 무작위로 고름
            val randomColor = listOf("orange", "yellow", "green", "blue").random()

            // 2. item_diary_comment.xml을 화면에 동적으로 생성(Inflate)
            val inflater = LayoutInflater.from(requireContext())
            val commentView = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

            // 3. 무작위 색상 SVG 파일 갈아끼우기 (댓글 리소스 맵 적용)
            val ivCommentBg = commentView.findViewById<ImageView>(R.id.ivCommentBg)
            val resId = commentResourceMap[randomColor] ?: R.drawable.comment_blue
            ivCommentBg.setImageResource(resId)

            val etCommentContent = commentView.findViewById<EditText>(R.id.etCommentContent)
            val btnCommentDone = commentView.findViewById<TextView>(R.id.btnCommentDone)
            val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime)

            val todayDateStr = dateFormat.format(Date())
            tvTime.text = todayDateStr

            commentView.tag = randomColor

            etCommentContent.isEnabled = true
            etCommentContent.isFocusable = true
            etCommentContent.isFocusableInTouchMode = true
            etCommentContent.requestFocus()

            // 4. 등록 버튼(TextView)을 누르면 입력된 값을 가져와서 Room DB에 최종 저장!
            btnCommentDone.setOnClickListener {
                val text = etCommentContent.text.toString().trim()
                if (text.isNotBlank()) {
                    etCommentContent.isEnabled = false
                    etCommentContent.clearFocus()
                    btnCommentDone.visibility = View.GONE

                    makeViewDraggable(commentView)
                    lockCommentEditText(commentView, etCommentContent)
                    insertCommentToDb(commentView, text, randomColor, todayDateStr)

                } else {
                    Toast.makeText(requireContext(), "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            // 5. 생성된 따끈따끈한 댓글 포스트잇을 화면 컨테이너에 즉시 추가
            binding.layoutCommentsContainer.addView(commentView)
            commentView.bringToFront()
            commentView.elevation = 10f
        }
    }

    private fun updateTitleDateText() {
        try {
            val sourceDate = dateFormat.parse(targetDate)
            if (sourceDate != null) {
                binding.tvDetailDateTitle.text = SimpleDateFormat("M월 d일", Locale.getDefault()).format(sourceDate)
            } else {
                binding.tvDetailDateTitle.text = targetDate
            }
        } catch (e: Exception) {
            binding.tvDetailDateTitle.text = targetDate
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parsedDate = dateFormat.parse(targetDate)
            if (parsedDate != null) cal.time = parsedDate
        } catch (_: Exception) {}

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val targetCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }

                val todayStr = dateFormat.format(Date())
                val selectedStr = dateFormat.format(targetCal.time)

                if (selectedStr == todayStr) {
                    // 💡 [핵심 1] 오늘 날짜 선택 시: 백스택에 쌓인 모든 상세 페이지를 비우고 메인(DiaryFragment)으로 깔끔하게 원복
                    parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                } else if (selectedStr != targetDate) {
                    // 💡 [핵심 2] 다른 과거 날짜 선택 시: 프래그먼트를 새로 쌓지 않고 현재 화면에서 날짜만 교체 후 데이터 재로드!
                    targetDate = selectedStr
                    updateTitleDateText()
                    loadDiaryAndComments()
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadDiaryAndComments() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val currentUid = AuthUtils.getCurrentUserId()

            val postIts = withContext(Dispatchers.IO) { db.diaryDao().getPostItsByDateAndUserId(targetDate,currentUid) }
            val comments = withContext(Dispatchers.IO) { db.diaryDao().getCommentsByDateAndUserId(targetDate,currentUid) }

            if (postIts.isNotEmpty()) {
                // DB에서 로드된 isHighlighted 값 확인
                isPastHighlighted = postIts.any { it.isHighlighted }
                Log.d("DiaryDetail", "불러온 날짜: $targetDate, 하이라이트 여부: $isPastHighlighted")

                // UI 즉시 반영
                applyCustomButtonState(binding.btnHighlightDetail, isSelected = isPastHighlighted)
            } else {
                isPastHighlighted = false
                applyCustomButtonState(binding.btnHighlightDetail, isSelected = false)
            }
            // 일기 데이터 그리기 (편집 불가능 구조)
            binding.layoutDetailDiaryContainer.removeAllViews()
            for (postIt in postIts) {
                val isDecoText = postIt.content.startsWith("[DECO]:")

                if (isDecoText) {
                    val pureText = postIt.content.replace("[DECO]:", "")
                    renderReadOnlyDecoText(pureText, postIt.positionX, postIt.positionY)
                } else {
                    renderReadOnlyPostIt(postIt)
                }
            }

            // 셀프 댓글 데이터 그리기 (계단식 뷰 스택)
            binding.layoutCommentsContainer.removeAllViews()
            for (comment in comments) {
                renderCommentPostIt(comment)
            }
        }
    }

    private fun renderReadOnlyDecoText(textStr: String, posX: Float, posY: Float) {
        val decorateTextView = TextView(requireContext()).apply {
            text = textStr
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 8, 16, 8)

            // 위치 지정
            translationX = posX
            translationY = posY

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        binding.layoutDetailDiaryContainer.addView(decorateTextView)
    }

    private fun insertCommentToDb(view: View, commentText: String, colorName: String, timestamp: String) {
        val currentUid = AuthUtils.getCurrentUserId()

        val posX = view.translationX
        val posY = view.translationY

        val newComment = CommentEntity(
            diaryId = 0,
            userId = currentUid,
            date = targetDate,
            content = commentText,
            color = colorName,
            timestamp = timestamp,
            posX = posX,
            posY = posY
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val savedId = db.diaryDao().insertComment(newComment)

            withContext(Dispatchers.Main) {
                view.setTag(R.id.ivCommentBg, savedId.toInt())
                Toast.makeText(requireContext(), "셀프 댓글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAllCommentsAndDiaryState() {
        val container = binding.layoutCommentsContainer
        val childCount = container.childCount
        val currentUid = AuthUtils.getCurrentUserId()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())

            // 1. 해당 날짜 일기 포스트잇들의 isHighlighted 및 visibility 일괄 업데이트
            val postIts = db.diaryDao().getPostItsByDateAndUserId(targetDate, currentUid)
            for (postIt in postIts) {
                val updatedPostIt = postIt.copy(
                    isHighlighted = isPastHighlighted,
                    visibility = currentVisibility
                )
                db.diaryDao().insertPostIt(updatedPostIt)
            }

            // 2. 댓글 위치 정보 및 신규 댓글 ID 저장
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext

                for (i in 0 until childCount) {
                    val commentView = container.getChildAt(i)
                    val etCommentContent = commentView.findViewById<EditText>(R.id.etCommentContent) ?: continue
                    val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime) ?: continue

                    val text = etCommentContent.text.toString().trim()
                    if (text.isNotBlank()) {
                        val existingCommentId = (commentView.getTag(R.id.ivCommentBg) as? Int) ?: 0
                        val colorName = (commentView.tag as? String) ?: "blue"
                        val posX = commentView.translationX
                        val posY = commentView.translationY
                        val commentDate = tvTime.text.toString()

                        val updatedComment = CommentEntity(
                            commentId = existingCommentId,
                            diaryId = 0,
                            userId = currentUid,
                            date = targetDate,
                            content = text,
                            color = colorName,
                            timestamp = commentDate,
                            posX = posX,
                            posY = posY
                        )

                        lifecycleScope.launch(Dispatchers.IO) {
                            val savedId = db.diaryDao().insertComment(updatedComment)
                            withContext(Dispatchers.Main) {
                                if (isAdded && _binding != null) {
                                    commentView.setTag(R.id.ivCommentBg, savedId.toInt())
                                }
                            }
                        }
                    }
                }
                Toast.makeText(requireContext(), "저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderReadOnlyPostIt(diary: DiaryEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_diary_postit, binding.layoutDetailDiaryContainer, false)

        val ivBg = view.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = view.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = view.findViewById<TextView>(R.id.etPostItContent)

        tvDate.text = diary.createdAt

        if (etContent is EditText) {
            etContent.setText(diary.content)
            // 형광펜 하이라이트가 있었다면 복원
            applyHighlightRangesToEditText(etContent, diary.highlightRanges)
            etContent.isEnabled = false
            etContent.isFocusable = false
        } else if (etContent is TextView) {
            etContent.text = diary.content
        }

        // 일기 원본 색상 SVG 매핑
        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg.setImageResource(resId)

        view.translationX = diary.positionX
        view.translationY = diary.positionY

        binding.layoutDetailDiaryContainer.addView(view)
    }

    // 과거의 일기를 켜서 '이미 저장되어 있던 댓글'들을 불러와서 그릴 때
    private fun renderCommentPostIt(comment: CommentEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

        val ivCommentBg = view.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = view.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = view.findViewById<TextView>(R.id.tvCommentTime)
        val btnCommentDone = view.findViewById<TextView>(R.id.btnCommentDone)

        // 기존 데이터 셋업 (CommentEntity 프로퍼티 바인딩)
        etCommentContent.setText(comment.content)
        tvTime.text = comment.timestamp.ifBlank { comment.date }

        val resId = commentResourceMap[comment.color] ?: R.drawable.comment_blue
        ivCommentBg.setImageResource(resId)

        view.setTag(R.id.ivCommentBg, comment.commentId)
        view.tag = comment.color

        view.translationX = comment.posX
        view.translationY = comment.posY

        // 이미 등록 완료된 댓글이므로 수정 및 터치 반응 원천 차단
        etCommentContent.isEnabled = false
        etCommentContent.isFocusable = false
        etCommentContent.isFocusableInTouchMode = false

        // 등록 버튼 제거 및 자유 드래그 활성화
        btnCommentDone.visibility = View.GONE
        makeViewDraggable(view)
        lockCommentEditText(view, etCommentContent)

        binding.layoutCommentsContainer.addView(view)
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun makeViewDraggable(view: View) {
        var lastX = 0f
        var lastY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    // 💡 애니메이션 대신 translation 좌표를 직접 이동시켜 누적 위치를 고정시킵니다.
                    v.translationX += dx
                    v.translationY += dy

                    // 다음 이동 거리 계산을 위해 기준점 갱신
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 손을 떼었을 때 현재 translationX, translationY 위치가 고정됩니다.
                    v.performClick()
                }
                else -> return@setOnTouchListener false
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun lockCommentEditText(commentView: View, etCommentContent: EditText) {
        // 1. 키보드 입력 및 포커스 차단 (수정 불가)
        etCommentContent.keyListener = null
        etCommentContent.isFocusable = false
        etCommentContent.isFocusableInTouchMode = false
        etCommentContent.isCursorVisible = false
        etCommentContent.clearFocus()
        etCommentContent.isEnabled = true // 터치 이벤트를 받기 위해 true 유지

        // 2. etCommentContent 터치 시 commentView 전체를 이동시킴
        var lastX = 0f
        var lastY = 0f

        etCommentContent.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    commentView.translationX += dx
                    commentView.translationY += dy

                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    commentView.performClick()
                }
                else -> return@setOnTouchListener false
            }
            true
        }
    }

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
    //수정사항503
}