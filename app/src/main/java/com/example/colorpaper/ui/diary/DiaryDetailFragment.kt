package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.HighlightEntity
import com.example.colorpaper.databinding.FragmentDiaryDetailBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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
    private var currentActivePostIt: View? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var buttonColorMap: Map<Button, Int> = emptyMap()
    private var isHighlightSelected: Boolean = false

    private var isEditMode: Boolean = true
    private var isFromArchive: Boolean = false
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
        targetDate = arguments?.getString(ARG_DATE) ?: arguments?.getString("TARGET_DATE") ?: dateFormat.format(Date())

        // 메인 하단바 클릭 진입 시 기본값은 true (사진 2: 수정 버튼 없음, 작성 모드)
        isEditMode = arguments?.getBoolean(ARG_IS_EDIT_MODE, true) ?: true
        // 아카이브 진입 여부 저장 (저장 후 원래 상태 복구용)
        isFromArchive = !isEditMode
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
            binding.btnHighlightDetail to "#D5B4B4".toColorInt(),
            binding.btnSaveDetail to "#867070".toColorInt()
        )

        setupSwipeGesture()

        binding.root.setOnTouchListener { _, event ->
            hideKeyboardAndClearFocus()
            if (!isEditMode) gestureDetector.onTouchEvent(event) else false
        }

        binding.ivFixedDiaryPageDetail.setOnTouchListener { _, event ->
            hideKeyboardAndClearFocus()
            if (!isEditMode) gestureDetector.onTouchEvent(event) else false
        }

        setupSuperJumpBar()

        // 아카이브 전용 [수정] 버튼 이벤트
        binding.btnEditMode.setOnClickListener {
            switchToEditMode()
        }

        loadDiaryAndComments()

        val todayStr = dateFormat.format(Date())
        val isFutureDate = targetDate > todayStr

        binding.btnToolbarAddDetail.setOnClickListener {
            if (isEditMode) addNewPostItField("yellow")
        }

        binding.btnToolbarPenDetail.setOnClickListener {
            if (isEditMode) applyHighlightToSelectedText()
        }

        binding.btnToolbarCommentDetail.alpha = if (isFutureDate) 0.3f else 1.0f
        binding.btnToolbarCommentDetail.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            val safeContext = context ?: return@setOnClickListener
            if (isFutureDate) {
                Toast.makeText(safeContext, "미래의 일기에는 댓글을 작성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val randomColor = listOf("orange", "yellow", "green", "blue").random()
            val inflater = LayoutInflater.from(safeContext)
            val commentView = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

            val ivCommentBg = commentView.findViewById<ImageView>(R.id.ivCommentBg)
            ivCommentBg.setImageResource(commentResourceMap[randomColor] ?: R.drawable.comment_blue)

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
                val currentSafeContext = context ?: return@setOnClickListener
                val text = etCommentContent.text.toString().trim()
                if (text.isNotBlank()) {
                    etCommentContent.isEnabled = false
                    etCommentContent.clearFocus()
                    btnCommentDone.visibility = View.GONE
                    makeViewDraggable(commentView)
                    insertCommentToDb(commentView, text, randomColor, todayDateStr)
                } else {
                    Toast.makeText(currentSafeContext, "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            binding.layoutCommentsContainer.addView(commentView)
            commentView.bringToFront()
            binding.layoutCommentsContainer.bringToFront()
        }

        binding.btnVisibilityDetail.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
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
            if (!isEditMode) return@setOnClickListener
            isHighlightSelected = !isHighlightSelected
            applyCustomButtonState(binding.btnHighlightDetail, isHighlightSelected)
            val message = if (isHighlightSelected) "하이라이트에 등록되었습니다." else "하이라이트가 해제되었습니다."
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveDetail.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            hideKeyboardAndClearFocus()
            saveCurrentDiaryWithPosition()
        }

        updateUiForCurrentMode()
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isEditMode || e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) moveDateByDays(-1) else moveDateByDays(1)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupSuperJumpBar() {
        binding.tvSuperJumpDate.text = targetDate
        binding.tvSuperJumpDate.setOnClickListener {
            if (!isEditMode) showDatePickerDialog()
        }

        binding.btnPrevDate.setOnClickListener {
            if (!isEditMode) moveDateByDays(-1)
        }

        binding.btnNextDate.setOnClickListener {
            if (!isEditMode) moveDateByDays(1)
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        try {
            val parsedDate = dateFormat.parse(targetDate)
            if (parsedDate != null) calendar.time = parsedDate
        } catch (_: Exception) {}

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                targetDate = dateFormat.format(calendar.time)
                binding.tvSuperJumpDate.text = targetDate
                loadDiaryAndComments()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun moveDateByDays(days: Int) {
        try {
            val calendar = Calendar.getInstance()
            val parsedDate = dateFormat.parse(targetDate)
            if (parsedDate != null) calendar.time = parsedDate
            calendar.add(Calendar.DAY_OF_MONTH, days)

            targetDate = dateFormat.format(calendar.time)
            binding.tvSuperJumpDate.text = targetDate
            loadDiaryAndComments()
        } catch (_: Exception) {}
    }

    private fun switchToEditMode() {
        isEditMode = true
        Toast.makeText(requireContext(), "수정 모드로 전환되었습니다.", Toast.LENGTH_SHORT).show()
        updateUiForCurrentMode()
        loadDiaryAndComments()
    }

    private fun updateUiForCurrentMode() {
        if (isEditMode) {
            // 메인 하단바 진입 시 기본 모드 (사진 2): 수정 버튼 숨김, 하단 버튼/툴바 표시
            binding.btnEditMode.visibility = View.GONE
            binding.layoutSuperJumpBar.alpha = 0.5f
            binding.btnPrevDate.isEnabled = false
            binding.btnNextDate.isEnabled = false
            binding.tvSuperJumpDate.isEnabled = false

            binding.layoutMetaActionsDetail.visibility = View.VISIBLE
            binding.layoutToolbarDecorateDetail.visibility = View.VISIBLE
        } else {
            // 마이페이지 아카이브 진입 시 (사진 1): 상단 수정 버튼만 표시
            binding.btnEditMode.visibility = View.VISIBLE
            binding.layoutSuperJumpBar.alpha = 1.0f
            binding.btnPrevDate.isEnabled = true
            binding.btnNextDate.isEnabled = true
            binding.tvSuperJumpDate.isEnabled = true

            binding.layoutMetaActionsDetail.visibility = View.GONE
            binding.layoutToolbarDecorateDetail.visibility = View.GONE
        }
    }

    private fun addNewPostItField(colorName: String = "yellow") {
        val safeContext = context ?: return
        val inflater = LayoutInflater.from(safeContext)
        val postItView = inflater.inflate(R.layout.item_diary_postit, binding.layoutDetailDiaryContainer, false)

        val ivBg = postItView.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = postItView.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = postItView.findViewById<EditText>(R.id.etPostItContent)

        tvDate.text = if (targetDate.isNotBlank()) targetDate else dateFormat.format(Date())
        ivBg.setImageResource(postItResourceMap[colorName] ?: R.drawable.post_yellow)

        etContent.isEnabled = true
        etContent.isFocusable = true
        etContent.isFocusableInTouchMode = true

        setupPostItEditTextTouch(etContent, postItView)
        postItView.tag = colorName
        makeViewDraggable(postItView)

        binding.layoutDetailDiaryContainer.addView(postItView)
        postItView.bringToFront()
        binding.layoutDetailDiaryContainer.bringToFront()

        currentActivePostIt = postItView
    }

    private fun applyHighlightToSelectedText() {
        val activeView = currentActivePostIt ?: run {
            Toast.makeText(requireContext(), "형광펜을 칠할 포스트잇을 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val etContent = activeView.findViewById<EditText>(R.id.etPostItContent) ?: return
        val start = etContent.selectionStart
        val end = etContent.selectionEnd

        if (start < 0 || end < 0 || start == end) {
            Toast.makeText(requireContext(), "형광펜을 칠할 텍스트 영역을 드래그하여 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val editableText = etContent.text
        val highlightedPart = editableText.substring(start, end)

        val spannable = if (editableText is Spannable) editableText else SpannableString(editableText)
        spannable.setSpan(
            BackgroundColorSpan("#FFF59D".toColorInt()),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        etContent.setText(spannable)
        etContent.setSelection(end)

        val existingDiaryId = (activeView.getTag(R.id.ivPostItBg) as? Int) ?: 0

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.diaryDao().insertHighlight(
                HighlightEntity(
                    diaryId = existingDiaryId,
                    date = targetDate,
                    highlightedText = highlightedPart
                )
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "'${highlightedPart}' 형광펜 텍스트가 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPostItEditTextTouch(etContent: EditText, postItView: View) {
        etContent.setOnTouchListener { _, event ->
            if (!isEditMode) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentActivePostIt = postItView
                    etContent.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    etContent.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    private fun getHighlightRangesFromEditText(etContent: EditText): String {
        val spannable = etContent.text as? Spannable ?: return ""
        val spans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
        val rangeList = mutableListOf<String>()

        for (span in spans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start in 0..<end) rangeList.add("$start-$end")
        }
        return rangeList.joinToString(",")
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

    private fun loadDiaryAndComments() {
        viewLifecycleOwner.lifecycleScope.launch {
            val safeContext = context ?: return@launch
            val db = AppDatabase.getDatabase(safeContext)

            val postIts = withContext(Dispatchers.IO) { db.diaryDao().getPostItsByDate(targetDate) }
            val comments = withContext(Dispatchers.IO) { db.diaryDao().getCommentsByDate(targetDate) }

            _binding?.let { binding ->
                isHighlightSelected = postIts.any { it.isHighlighted }
                applyCustomButtonState(binding.btnHighlightDetail, isHighlightSelected)

                binding.layoutDetailDiaryContainer.removeAllViews()
                for (postIt in postIts) {
                    val isDecoText = postIt.content.startsWith("[DECO]:")

                    if (isDecoText) {
                        val pureText = postIt.content.replace("[DECO]:", "")
                        renderReadOnlyDecoText(pureText, postIt.positionX, postIt.positionY)
                    } else {
                        renderPostIt(postIt)
                    }
                }

                binding.layoutCommentsContainer.removeAllViews()
                for (comment in comments) {
                    renderCommentPostIt(comment)
                }
            }
        }
    }

    private fun renderReadOnlyDecoText(textStr: String, posX: Float, posY: Float) {
        val safeContext = context ?: return
        val binding = _binding ?: return

        val decorateTextView = TextView(safeContext).apply {
            text = textStr
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 8, 16, 8)
            translationX = posX
            translationY = posY
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        binding.layoutDetailDiaryContainer.addView(decorateTextView)
    }

    private fun renderPostIt(diary: DiaryEntity) {
        val safeContext = context ?: return
        val binding = _binding ?: return

        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_postit, binding.layoutDetailDiaryContainer, false)

        val ivBg = view.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = view.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = view.findViewById<EditText>(R.id.etPostItContent)

        tvDate.text = diary.createdAt

        if (etContent != null) {
            etContent.setText(diary.content)
            applyHighlightRangesToEditText(etContent, diary.highlightRanges)

            etContent.isEnabled = isEditMode
            etContent.isFocusable = isEditMode
            etContent.isFocusableInTouchMode = isEditMode
            setupPostItEditTextTouch(etContent, view)
        }

        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg.setImageResource(resId)

        view.translationX = diary.positionX
        view.translationY = diary.positionY
        view.setTag(R.id.ivPostItBg, diary.diaryId)
        view.tag = diary.color

        makeViewDraggable(view)
        binding.layoutDetailDiaryContainer.addView(view)
    }

    private fun renderCommentPostIt(comment: CommentEntity) {
        val safeContext = context ?: return
        val binding = _binding ?: return

        val inflater = LayoutInflater.from(safeContext)
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

    private fun insertCommentToDb(view: View, commentText: String, colorName: String, timestamp: String) {
        val safeContext = context ?: return
        val posX = view.translationX
        val posY = view.translationY

        val newComment = CommentEntity(
            diaryId = 0,
            userId = "1",
            date = targetDate,
            content = commentText,
            color = colorName,
            timestamp = timestamp,
            posX = posX,
            posY = posY
        )

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(safeContext)
            val savedId = db.diaryDao().insertComment(newComment)

            withContext(Dispatchers.Main) {
                _binding?.let {
                    view.setTag(R.id.ivCommentBg, savedId.toInt())
                    Toast.makeText(safeContext, "셀프 댓글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveCurrentDiaryWithPosition() {
        val safeContext = context ?: return
        val container = binding.layoutDetailDiaryContainer
        val childCount = container.childCount

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(safeContext)

            for (i in 0 until childCount) {
                val childView = withContext(Dispatchers.Main) { container.getChildAt(i) }
                val etContent = childView.findViewById<EditText>(R.id.etPostItContent) ?: continue
                val contentText = etContent.text.toString().trim()

                if (contentText.isNotBlank()) {
                    val existingDiaryId = (childView.getTag(R.id.ivPostItBg) as? Int) ?: 0
                    val postItColor = (childView.tag as? String) ?: "yellow"
                    val posX = childView.translationX
                    val posY = childView.translationY

                    val highlightRanges = withContext(Dispatchers.Main) {
                        getHighlightRangesFromEditText(etContent)
                    }

                    val newDiary = DiaryEntity(
                        diaryId = existingDiaryId,
                        createdAt = targetDate.ifBlank { dateFormat.format(Date()) },
                        content = contentText,
                        color = postItColor,
                        tag = "",
                        emotionStamp = "",
                        isHighlighted = isHighlightSelected,
                        visibility = currentVisibility,
                        positionX = posX,
                        positionY = posY,
                        userId = "1",
                        highlightRanges = highlightRanges
                    )

                    val savedId = db.diaryDao().insertPostIt(newDiary)
                    withContext(Dispatchers.Main) {
                        childView.setTag(R.id.ivPostItBg, savedId.toInt())
                    }
                }
            }

            saveAllCommentsPositions()

            withContext(Dispatchers.Main) {
                Toast.makeText(safeContext, "일기가 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
                // 아카이브에서 넘어와서 [수정]을 누르고 저장한 경우에만 조회 모드로 복구
                if (isFromArchive) {
                    isEditMode = false
                    updateUiForCurrentMode()
                }
                loadDiaryAndComments()
            }
        }
    }

    private fun saveAllCommentsPositions() {
        val safeContext = context ?: return
        val container = binding.layoutCommentsContainer
        val childCount = container.childCount

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
                    userId = "1",
                    date = targetDate,
                    content = text,
                    color = colorName,
                    timestamp = commentDate,
                    posX = posX,
                    posY = posY
                )

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(safeContext)
                    val savedId = db.diaryDao().insertComment(updatedComment)
                    withContext(Dispatchers.Main) {
                        commentView.setTag(R.id.ivCommentBg, savedId.toInt())
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeViewDraggable(view: View) {
        var lastX = 0f
        var lastY = 0f

        view.setOnTouchListener { v, event ->
            if (!isEditMode) return@setOnTouchListener false

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hideKeyboardAndClearFocus() {
        val currentFocusedView = activity?.currentFocus
        if (currentFocusedView != null) {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)
            currentFocusedView.clearFocus()
        }
    }

    companion object {
        private const val ARG_DATE = "arg_date"
        private const val ARG_IS_EDIT_MODE = "arg_is_edit_mode"

        // isEditMode의 기본값을 true로 지정하여 메인 하단바 진입 시 항상 사진 2 형태로 노출
        fun newInstance(date: String, isEditMode: Boolean = true): DiaryDetailFragment {
            val fragment = DiaryDetailFragment()
            val args = Bundle().apply {
                putString(ARG_DATE, date)
                putBoolean(ARG_IS_EDIT_MODE, isEditMode)
            }
            fragment.arguments = args
            return fragment
        }
    }
}