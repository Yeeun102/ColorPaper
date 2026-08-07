package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.GestureDetector
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
import com.example.colorpaper.MainActivity
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
import kotlin.math.abs

class DiaryDetailFragment : Fragment() {
    private var _binding: FragmentDiaryDetailBinding? = null
    private val binding get() = _binding!!

    private val postItResourceMap = mapOf(
        "orange" to R.drawable.post_orange,
        "yellow" to R.drawable.post_yellow,
        "green"  to R.drawable.post_green,
        "blue"   to R.drawable.post_blue
    )

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
    private lateinit var gestureDetector: GestureDetector

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonColorMap = mapOf(
            binding.btnVisibilityDetail to "#E4D0D0".toColorInt(),
            binding.btnSaveDetail to "#867070".toColorInt()
        )

        // 💡 1. 상단 툴바 뒤로가기 버튼 클릭 시 마이페이지(이전 화면)로 돌아가기
        binding.btnToolbarBackDetail.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 💡 2. 스와이프 제스처 감지기 설정 (오른쪽으로 밀면 이전 날짜, 왼쪽으로 밀면 다음 날짜)
        setupSwipeGesture()

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        updateTitleDateText()

        binding.btnDatePickerDetail.setOnClickListener {
            showDatePicker()
        }

        binding.btnSaveDetail.visibility = View.VISIBLE

        binding.btnToolbarAddDetail.isEnabled = false
        binding.btnToolbarAddDetail.alpha = 0.3f

        loadDiaryAndComments()

        binding.btnSaveDetail.setOnClickListener {
            saveAllCommentsPositions()
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
            val randomColor = listOf("orange", "yellow", "green", "blue").random()

            val inflater = LayoutInflater.from(requireContext())
            val commentView = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

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

            btnCommentDone.setOnClickListener {
                val text = etCommentContent.text.toString().trim()
                if (text.isNotBlank()) {
                    etCommentContent.isEnabled = false
                    etCommentContent.clearFocus()
                    btnCommentDone.visibility = View.GONE

                    makeViewDraggable(commentView)
                    insertCommentToDb(commentView, text, randomColor, todayDateStr)

                } else {
                    Toast.makeText(requireContext(), "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            binding.layoutCommentsContainer.addView(commentView)
            commentView.bringToFront()
            binding.layoutCommentsContainer.bringToFront()
        }
    }

    // 💡 스와이프 제스처 설정 함수
    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 👈 손가락을 오른쪽으로 밀었을 때: 이전 날짜 (-1일)
                            changeDateByAmount(-1)
                        } else {
                            // 👉 손가락을 왼쪽으로 밀었을 때: 다음 날짜 (+1일)
                            changeDateByAmount(1)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // 💡 스와이프에 의해 날짜 변경 및 재로드 처리
    private fun changeDateByAmount(amount: Int) {
        try {
            val parsedDate = dateFormat.parse(targetDate) ?: return
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                add(Calendar.DAY_OF_MONTH, amount)
            }
            targetDate = dateFormat.format(cal.time)

            updateTitleDateText()
            loadDiaryAndComments()
        } catch (e: Exception) {
            e.printStackTrace()
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
                    parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                } else if (selectedStr != targetDate) {
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
        viewLifecycleOwner.lifecycleScope.launch {
            val safeContext = context ?: return@launch
            val db = AppDatabase.getDatabase(safeContext)
            val currentUid = AuthUtils.getCurrentUserId()

            val postIts = withContext(Dispatchers.IO) { db.diaryDao().getPostItsByDateAndUserId(targetDate, currentUid) }
            val comments = withContext(Dispatchers.IO) { db.diaryDao().getCommentsByDateAndUserId(targetDate, currentUid) }

            val currentBinding = _binding ?: return@launch

            currentBinding.layoutDetailDiaryContainer.removeAllViews()
            for (postIt in postIts) {
                val isDecoText = postIt.content.startsWith("[DECO]:")

                if (isDecoText) {
                    val pureText = postIt.content.replace("[DECO]:", "")
                    renderReadOnlyDecoText(pureText, postIt.positionX, postIt.positionY)
                } else {
                    renderReadOnlyPostIt(postIt)
                }
            }

            currentBinding.layoutCommentsContainer.removeAllViews()
            for (comment in comments) {
                renderCommentPostIt(comment)
            }
        }
    }

    private fun renderReadOnlyDecoText(textStr: String, posX: Float, posY: Float) {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val density = safeContext.resources.displayMetrics.density
        val paddingHorizontal = (16 * density).toInt()
        val paddingVertical = (8 * density).toInt()

        val decorateTextView = TextView(safeContext).apply {
            text = textStr
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

            translationX = posX
            translationY = posY

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        currentBinding.layoutDetailDiaryContainer.addView(decorateTextView)
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

    private fun saveAllCommentsPositions() {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val container = currentBinding.layoutCommentsContainer
        val childCount = container.childCount

        if (childCount == 0) {
            Toast.makeText(safeContext, "저장할 댓글이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUid = AuthUtils.getCurrentUserId()
        val commentsToSave = mutableListOf<Pair<View, CommentEntity>>()

        for (i in 0 until childCount) {
            val commentView = container.getChildAt(i) ?: continue
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

                commentsToSave.add(Pair(commentView, updatedComment))
            }
        }

        if (commentsToSave.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(safeContext.applicationContext)

            val savedResults = withContext(Dispatchers.IO) {
                commentsToSave.map { (view, comment) ->
                    val savedId = db.diaryDao().insertComment(comment)
                    Pair(view, savedId)
                }
            }

            val activeBinding = _binding ?: return@launch
            val activeContext = context ?: return@launch

            for ((view, savedId) in savedResults) {
                view.setTag(R.id.ivCommentBg, savedId.toInt())
            }

            Toast.makeText(activeContext, "댓글 위치가 저장되었습니다!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderReadOnlyPostIt(diary: DiaryEntity) {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_postit, currentBinding.layoutDetailDiaryContainer, false)

        val ivBg = view.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = view.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = view.findViewById<TextView>(R.id.etPostItContent)

        tvDate?.text = diary.createdAt

        if (etContent is EditText) {
            etContent.setText(diary.content)
            applyHighlightRangesToEditText(etContent, diary.highlightRanges)
            etContent.isEnabled = false
            etContent.isFocusable = false
        } else {
            etContent?.text = diary.content
        }

        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg?.setImageResource(resId)

        view.translationX = diary.positionX
        view.translationY = diary.positionY

        currentBinding.layoutDetailDiaryContainer.addView(view)
    }

    private fun renderCommentPostIt(comment: CommentEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

        val ivCommentBg = view.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = view.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = view.findViewById<TextView>(R.id.tvCommentTime)
        val btnCommentDone = view.findViewById<TextView>(R.id.btnCommentDone)

        etCommentContent.setText(comment.content)
        tvTime.text = comment.timestamp.ifBlank { comment.date }

        val resId = commentResourceMap[comment.color] ?: R.drawable.comment_blue
        ivCommentBg.setImageResource(resId)

        view.setTag(R.id.ivCommentBg, comment.commentId)
        view.tag = comment.color

        view.translationX = comment.posX
        view.translationY = comment.posY

        etCommentContent.isEnabled = false
        etCommentContent.isFocusable = false
        etCommentContent.isFocusableInTouchMode = false

        btnCommentDone.visibility = View.GONE
        makeViewDraggable(view)

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

                    v.translationX += dx
                    v.translationY += dy

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

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavVisibility(false)
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.setBottomNavVisibility(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}