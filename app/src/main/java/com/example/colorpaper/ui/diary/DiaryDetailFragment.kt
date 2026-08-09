package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryCommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.databinding.FragmentDiaryDetailBinding
import com.example.colorpaper.util.AuthUtils
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DiaryDetailFragment : Fragment() {
    // binding getter(!! 사용)를 제거하여 NullPointerException 근본 원인 차단
    private var _binding: FragmentDiaryDetailBinding? = null

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var targetDate: String = dateFormat.format(Date())
    private var targetUserId: String? = null
    private var isMyDiary: Boolean = true
    private var currentVisibility: String = "전체공개"

    private var buttonColorMap: Map<Button, Int> = emptyMap()
    private lateinit var gestureDetector: GestureDetector

    private var isPastHighlighted = false

    companion object {
        private const val ARG_TARGET_DATE = "TARGET_DATE"
        private const val ARG_TARGET_USER_ID = "TARGET_USER_ID"

        fun newInstance(targetDate: String? = null, targetUserId: String? = null): DiaryDetailFragment {
            return DiaryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_DATE, targetDate)
                    putString(ARG_TARGET_USER_ID, targetUserId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val argDate = arguments?.getString(ARG_TARGET_DATE)
        if (!argDate.isNullOrBlank()) {
            targetDate = argDate
        }

        targetUserId = arguments?.getString(ARG_TARGET_USER_ID)

        val myUid = auth.currentUser?.uid
        isMyDiary = targetUserId.isNullOrEmpty() || targetUserId == myUid
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryDetailBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentBinding = _binding ?: return

        buttonColorMap = mapOf(
            //currentBinding.btnVisibilityDetail to "#E4D0D0".toColorInt(),
            currentBinding.btnSaveDetail to "#867070".toColorInt(),
            currentBinding.btnHighlightDetail to "#D5B4B4".toColorInt()
        )

        currentBinding.btnToolbarBackDetail.setOnClickListener {
            if (isAdded) {
                parentFragmentManager.popBackStack()
            }
        }

        setupSwipeGesture()

        currentBinding.root.setOnTouchListener { _, event ->
            if (::gestureDetector.isInitialized) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }

        updateTitleDateText()

        currentBinding.btnDatePickerDetail.setOnClickListener {
            showDatePicker()
        }

        currentBinding.btnSaveDetail.visibility = View.VISIBLE
        currentBinding.btnToolbarAddDetail.isEnabled = false
        currentBinding.btnToolbarAddDetail.alpha = 0.3f

        checkFollowStateAndLoad()

        currentBinding.btnSaveDetail.setOnClickListener {
            saveAllCommentsAndDiaryState()
        }

        currentBinding.btnHighlightDetail.setOnClickListener {
            val safeContext = context?.applicationContext ?: return@setOnClickListener
            val currentUid = AuthUtils.getCurrentUserId()

            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(safeContext)

                val postIts = withContext(Dispatchers.IO) {
                    db.diaryDao().getPostItsByDateAndUserId(targetDate, currentUid)
                }

                val binding = _binding ?: return@launch
                if (!isAdded) return@launch

                if (postIts.isEmpty()) {
                    Toast.makeText(safeContext, "하이라이트에 등록할 다이어리가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                isPastHighlighted = !isPastHighlighted
                applyCustomButtonState(binding.btnHighlightDetail, isSelected = isPastHighlighted)

                withContext(Dispatchers.IO) {
                    for (postIt in postIts) {
                        val updatedPostIt = postIt.copy(isHighlighted = isPastHighlighted)
                        db.diaryDao().insertPostIt(updatedPostIt)
                    }
                }

                if (isAdded && _binding != null) {
                    val msg = if (isPastHighlighted) "하이라이트에 등록되었습니다." else "하이라이트 등록이 해제되었습니다."
                    Toast.makeText(safeContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val todayStr = dateFormat.format(Date())
        val isFutureDate = targetDate > todayStr

        if (isFutureDate) {
            currentBinding.btnToolbarCommentDetail.alpha = 0.3f
        } else {
            currentBinding.btnToolbarCommentDetail.alpha = 1.0f
        }

        currentBinding.btnToolbarCommentDetail.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            val activeBinding = _binding ?: return@setOnClickListener

            if (isFutureDate) {
                Toast.makeText(safeContext, "미래의 일기에는 댓글을 작성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val randomColor = listOf("orange", "yellow", "green", "blue").random()

            val inflater = LayoutInflater.from(safeContext)
            val commentView = inflater.inflate(R.layout.item_diary_comment, activeBinding.layoutCommentsContainer, false)

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
                val safeCtx = context ?: return@setOnClickListener
                val text = etCommentContent.text.toString().trim()
                if (text.isNotBlank()) {
                    etCommentContent.isEnabled = false
                    etCommentContent.clearFocus()
                    btnCommentDone.visibility = View.GONE

                    makeViewDraggable(commentView)
                    lockCommentEditText(commentView, etCommentContent)
                    insertCommentToDb(commentView, text, randomColor, todayDateStr)

                    val targetUid = targetUserId ?: auth.currentUser?.uid ?: ""
                    saveComment(101L, targetUid, "❤️", text)
                } else {
                    Toast.makeText(safeCtx, "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            activeBinding.layoutCommentsContainer.addView(commentView)
            commentView.bringToFront()
            commentView.elevation = 10f
            activeBinding.layoutCommentsContainer.bringToFront()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavVisibility(false)
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.setBottomNavVisibility(true)
    }

    private fun applyCustomButtonState(button: Button, isSelected: Boolean, originalColor: Int = 0) {
        if (!isAdded) return
        if (button is MaterialButton) {
            val defaultColor = if (originalColor != 0) originalColor else (buttonColorMap[button] ?: "#E4D0D0".toColorInt())

            if (isSelected) {
                button.backgroundTintList = ColorStateList.valueOf("#FFF59D".toColorInt())
            } else {
                button.backgroundTintList = ColorStateList.valueOf(defaultColor)
            }
            button.strokeWidth = 0
            button.invalidate()
            button.refreshDrawableState()
        }
    }

    private fun checkFollowStateAndLoad() {
        if (isMyDiary) {
            loadDiaryAndComments()
        } else {
            val safeContext = context?.applicationContext ?: return
            val myUid = auth.currentUser?.uid ?: return
            val friendUid = targetUserId ?: return

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val followDoc = withContext(Dispatchers.IO) {
                        firestore.collection("users")
                            .document(myUid)
                            .collection("following")
                            .document(friendUid)
                            .get()
                            .await()
                    }

                    if (!isAdded || _binding == null) return@launch

                    if (followDoc.exists()) {
                        loadDiaryAndComments()
                    } else {
                        Toast.makeText(safeContext, "팔로우 중인 친구의 다이어리만 조회할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("DiaryDetail", "팔로우 상태 확인 실패", e)
                    if (isAdded && _binding != null) {
                        loadDiaryAndComments()
                    }
                }
            }
        }
    }

    fun saveComment(diaryId: Long, targetUserId: String, emoji: String, content: String) {
        val safeContext = context?.applicationContext ?: return
        val myUid = auth.currentUser?.uid ?: return
        val currentDate = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())

        val commentEntity = DiaryCommentEntity(
            diaryId = diaryId,
            writerId = myUid.hashCode(),
            writerName = "나으닝",
            ownerId = targetUserId.hashCode(),
            emoji = emoji,
            content = content,
            createdAt = currentDate
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(safeContext)
                    db.diaryCommentDao().insertComment(commentEntity)
                }

                firestore.collection("diary_comments")
                    .add(commentEntity)
                    .await()

                if (isAdded && _binding != null) {
                    Toast.makeText(safeContext, "댓글 반응 등록 완료! 🎉", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DiaryDetail", "댓글 저장 중 에러 발생 (DB/Firestore)", e)
            }
        }
    }

    private fun setupSwipeGesture() {
        val safeContext = context ?: return
        gestureDetector = GestureDetector(safeContext, object : GestureDetector.SimpleOnGestureListener() {
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
                            changeDateByAmount(-1)
                        } else {
                            changeDateByAmount(1)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun changeDateByAmount(amount: Int) {
        try {
            val parsedDate = dateFormat.parse(targetDate) ?: return
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                add(Calendar.DAY_OF_MONTH, amount)
            }
            targetDate = dateFormat.format(cal.time)

            updateTitleDateText()
            checkFollowStateAndLoad()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTitleDateText() {
        val currentBinding = _binding ?: return
        try {
            val sourceDate = dateFormat.parse(targetDate)
            if (sourceDate != null) {
                currentBinding.tvDetailDateTitle.text = SimpleDateFormat("M월 d일", Locale.getDefault()).format(sourceDate)
            } else {
                currentBinding.tvDetailDateTitle.text = targetDate
            }
        } catch (e: Exception) {
            currentBinding.tvDetailDateTitle.text = targetDate
        }
    }

    private fun showDatePicker() {
        val safeContext = context ?: return
        val cal = Calendar.getInstance()
        try {
            val parsedDate = dateFormat.parse(targetDate)
            if (parsedDate != null) cal.time = parsedDate
        } catch (_: Exception) {}

        DatePickerDialog(
            safeContext,
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
                    checkFollowStateAndLoad()
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadDiaryAndComments() {
        viewLifecycleOwner.lifecycleScope.launch {
            val safeContext = context?.applicationContext ?: return@launch
            val myUid = auth.currentUser?.uid ?: ""
            val effectiveUidString = if (isMyDiary) myUid else (targetUserId ?: myUid)

            var postIts = emptyList<DiaryEntity>()
            var comments = emptyList<CommentEntity>()

            try {
                val querySnap = firestore.collection("diaries")
                    .whereEqualTo("userId", effectiveUidString)
                    .whereEqualTo("createdAt", targetDate)
                    .get()
                    .await()

                postIts = querySnap.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(DiaryEntity::class.java)
                    } catch (e: Exception) {
                        Log.e("DiaryDetail", "Firestore 객체 변환 실패: ${doc.id}", e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("DiaryDetail", "Firestore 조회 실패", e)
            }

            try {
                val db = AppDatabase.getDatabase(safeContext)
                if (postIts.isEmpty()) {
                    postIts = withContext(Dispatchers.IO) {
                        db.diaryDao().getPostItsByDateAndUserId(targetDate, effectiveUidString)
                    }
                }

                comments = withContext(Dispatchers.IO) {
                    db.diaryDao().getCommentsByDateAndUserId(targetDate, effectiveUidString)
                }
            } catch (e: Exception) {
                Log.e("DiaryDetail", "로컬 DB 조회 실패", e)
            }

            // 비동기 작업 종료 직후 _binding 및 Lifecycle을 안전하게 확인
            val binding = _binding ?: return@launch
            if (!isAdded) return@launch

            isPastHighlighted = postIts.any { it.isHighlighted }
            applyCustomButtonState(binding.btnHighlightDetail, isSelected = isPastHighlighted)

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

            binding.layoutCommentsContainer.removeAllViews()
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
            etContent.isEnabled = false
            etContent.isFocusable = false
            applyHighlightRangesToEditText(etContent, diary.highlightRanges ?: "")
        } else {
            etContent?.text = diary.content
        }

        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg?.setImageResource(resId)

        view.translationX = diary.positionX
        view.translationY = diary.positionY

        currentBinding.layoutDetailDiaryContainer.addView(view)
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

    private fun renderCommentPostIt(comment: CommentEntity) {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_comment, currentBinding.layoutCommentsContainer, false)

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

        btnCommentDone.visibility = View.GONE
        makeViewDraggable(view)
        lockCommentEditText(view, etCommentContent)

        currentBinding.layoutCommentsContainer.addView(view)
    }

    private fun insertCommentToDb(view: View, commentText: String, colorName: String, timestamp: String) {
        val safeContext = context?.applicationContext ?: return
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

        viewLifecycleOwner.lifecycleScope.launch {
            val savedId = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(safeContext)
                db.diaryDao().insertComment(newComment)
            }

            if (_binding == null || !isAdded) return@launch

            view.setTag(R.id.ivCommentBg, savedId.toInt())
            Toast.makeText(safeContext, "셀프 댓글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAllCommentsAndDiaryState() {
        val safeContext = context?.applicationContext ?: return
        val currentBinding = _binding ?: return

        val container = currentBinding.layoutCommentsContainer
        val childCount = container.childCount
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

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(safeContext)

            withContext(Dispatchers.IO) {
                val postIts = db.diaryDao().getPostItsByDateAndUserId(targetDate, currentUid)
                for (postIt in postIts) {
                    val updatedPostIt = postIt.copy(
                        isHighlighted = isPastHighlighted,
                        visibility = currentVisibility
                    )
                    db.diaryDao().insertPostIt(updatedPostIt)
                }
            }

            if (commentsToSave.isNotEmpty()) {
                val savedResults = withContext(Dispatchers.IO) {
                    commentsToSave.map { (view, comment) ->
                        val savedId = db.diaryDao().insertComment(comment)
                        Pair(view, savedId)
                    }
                }

                if (isAdded && _binding != null) {
                    for ((view, savedId) in savedResults) {
                        view.setTag(R.id.ivCommentBg, savedId.toInt())
                    }
                }
            }

            if (isAdded && _binding != null) {
                Toast.makeText(safeContext, "저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
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
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun lockCommentEditText(commentView: View, etCommentContent: EditText) {
        etCommentContent.keyListener = null
        etCommentContent.isFocusable = false
        etCommentContent.isFocusableInTouchMode = false
        etCommentContent.isCursorVisible = false
        etCommentContent.clearFocus()
        etCommentContent.isEnabled = true

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
