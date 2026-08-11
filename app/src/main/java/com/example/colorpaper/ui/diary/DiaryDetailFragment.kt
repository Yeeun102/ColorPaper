package com.example.colorpaper.ui.diary

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.example.colorpaper.ui.profile.ProfileFragment
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.databinding.FragmentDiaryDetailBinding
import com.example.colorpaper.util.AuthUtils
import com.example.colorpaper.ui.theme.AppTheme
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.hypot
import androidx.core.view.isVisible

class DiaryDetailFragment : Fragment() {
    private var _binding: FragmentDiaryDetailBinding? = null
    private val binding get() = _binding!!

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
    private var isReadOnlyMode: Boolean = false
    private var currentVisibility: String = "전체공개"

    private var buttonColorMap: Map<Button, Int> = emptyMap()
    private lateinit var gestureDetector: GestureDetector

    private var isPastHighlighted = false
    private var currentPostIts: List<DiaryEntity> = emptyList()
    private var isFollowingUser: Boolean = true

    private val palette by lazy { ThemeManager.currentPalette(requireContext())}
    private var isHighlightMode: Boolean = false

    companion object {
        private const val ARG_TARGET_DATE = "TARGET_DATE"
        private const val ARG_TARGET_USER_ID = "TARGET_USER_ID"
        private const val ARG_READ_ONLY = "READ_ONLY"
        private const val COMMENT_SEPARATOR = "||"
        private const val DEFAULT_COMMENT_EMOJI = "💬"

        fun newInstance(
            targetDate: String? = null,
            targetUserId: String? = null,
            readOnly: Boolean = false,
            isHighlightMode: Boolean = false
        ): DiaryDetailFragment {
            return DiaryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_DATE, targetDate)
                    putString(ARG_TARGET_USER_ID, targetUserId)
                    putBoolean(ARG_READ_ONLY, readOnly || isHighlightMode)
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
        isReadOnlyMode = arguments?.getBoolean(ARG_READ_ONLY, false) ?: false
        isHighlightMode = isReadOnlyMode

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
        currentBinding.ivFixedDiaryPageDetail.setImageResource(diaryPageResource())

        buttonColorMap = mapOf(
            currentBinding.btnHighlightDetail to ContextCompat.getColor(requireContext(), palette.reminder),
            currentBinding.btnSaveDetail to ContextCompat.getColor(requireContext(), palette.accent)
        )

        currentBinding.btnToolbarBackDetail.setOnClickListener {
            if (isAdded) {
                parentFragmentManager.popBackStack()
            }
        }

        updateTitleDateText()
        applyEntryModeUi()

        if (canNavigateDate()) {
            setupSwipeGesture()
            // 👈 [수정] 무조건 true를 리턴하지 않고 제스처 결과만 반환하도록 변경
            currentBinding.root.setOnTouchListener { _, event ->
                if (::gestureDetector.isInitialized) {
                    gestureDetector.onTouchEvent(event)
                } else {
                    false
                }
            }
            currentBinding.btnDatePickerDetail.setOnClickListener {
                showDatePicker()
            }
        } else {
            currentBinding.root.setOnTouchListener(null)
            currentBinding.btnDatePickerDetail.setOnClickListener(null)
        }

        currentBinding.btnToolbarAddDetail.isEnabled = false
        currentBinding.btnToolbarAddDetail.alpha = 0.3f
        currentBinding.btnToolbarPenDetail.isEnabled = false
        currentBinding.btnToolbarPenDetail.alpha = 0.3f
        currentBinding.btnToolbarTextDetail.isEnabled = false
        currentBinding.btnToolbarTextDetail.alpha = 0.3f

        checkFollowStateAndLoad()

        if (!isReadOnlyMode) {
            currentBinding.btnSaveDetail.setOnClickListener {
                saveAllCommentsAndDiaryState()
            }

            currentBinding.btnHighlightDetail.setOnClickListener {
                val safeContext = context?.applicationContext ?: return@setOnClickListener
                val currentUid = AuthUtils.getCurrentUserId()

                viewLifecycleOwner.lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(safeContext)

                    var postIts = currentPostIts
                    if (postIts.isEmpty()) {
                        postIts = withContext(Dispatchers.IO) {
                            db.diaryDao().getPostItsByDateAndUserId(targetDate, currentUid)
                        }
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
        }

        if (isCurrentDateOrFuture() || isReadOnlyMode) {
            currentBinding.btnToolbarCommentDetail.alpha = 0.3f
        } else {
            currentBinding.btnToolbarCommentDetail.alpha = 1.0f
        }

        currentBinding.btnToolbarCommentDetail.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            val activeBinding = _binding ?: return@setOnClickListener

            if (isReadOnlyMode) {
                Toast.makeText(safeContext, "하이라이트에서 연 화면은 읽기 전용입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isCurrentDateOrFuture()) {
                Toast.makeText(safeContext, "오늘 이후의 일기에는 댓글을 작성할 수 없습니다.", Toast.LENGTH_SHORT).show()
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
            val tvAuthor = commentView.findViewById<TextView>(R.id.tvCommentAuthor)

            val todayDateStr = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())
            tvTime.visibility = View.VISIBLE
            tvTime.text = "오늘"
            tvAuthor.text = "작성자 : 나"

            commentView.tag = randomColor

            etCommentContent.isEnabled = true
            etCommentContent.isFocusable = true
            etCommentContent.isFocusableInTouchMode = true
            etCommentContent.requestFocus()
            limitEditTextToPostItBounds(etCommentContent, maxLines = 3)

            btnCommentDone.setOnClickListener {
                val safeCtx = context ?: return@setOnClickListener
                val text = etCommentContent.text.toString().trim()
                if (text.isNotBlank()) {
                    etCommentContent.isEnabled = false
                    etCommentContent.clearFocus()
                    btnCommentDone.visibility = View.GONE

                    makeViewDraggable(commentView)
                    lockCommentEditText(commentView, etCommentContent)

                    val currentZIndex = activeBinding.layoutCommentsContainer.indexOfChild(commentView).coerceAtLeast(0)
                    insertCommentToDb(commentView, text, randomColor, todayDateStr, currentZIndex)
                } else {
                    Toast.makeText(safeCtx, "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            activeBinding.layoutCommentsContainer.addView(commentView)
            commentView.post { clampViewToParent(commentView) }
            commentView.bringToFront()
            commentView.elevation = 10f
            activeBinding.layoutCommentsContainer.bringToFront()
        }
        applyToolbarThemeColor()
    }


    private fun applyToolbarThemeColor() {
        val toolbarColor = ContextCompat.getColor(requireContext(), palette.yearsAgo)
        val toolbarStrokeColor = ContextCompat.getColor(requireContext(), palette.stroke)
        val highlightButton = ContextCompat.getColor(requireContext(), palette.reminder)
        binding.layoutToolbarDecorateDetail.setCardBackgroundColor(toolbarColor)
        binding.layoutToolbarDecorateDetail.strokeColor = toolbarStrokeColor
        binding.btnSaveDetail.backgroundTintList = ColorStateList.valueOf(toolbarStrokeColor)
        binding.btnHighlightDetail.backgroundTintList = ColorStateList.valueOf(highlightButton)
    }

    private fun isProfileEntry(): Boolean = isMyDiary && !isReadOnlyMode && !targetUserId.isNullOrBlank()

    private fun canNavigateDate(): Boolean = !isReadOnlyMode

    private fun applyEntryModeUi() {
        val currentBinding = _binding ?: return

        if (isReadOnlyMode) {
            currentBinding.layoutMetaActionsDetail.visibility = View.GONE
            currentBinding.layoutToolbarDecorateDetail.visibility = View.GONE
            currentBinding.btnDatePickerDetail.visibility = View.GONE
            currentBinding.btnDatePickerDetail.isEnabled = false
            return
        }

        currentBinding.layoutMetaActionsDetail.visibility = View.VISIBLE
        currentBinding.layoutToolbarDecorateDetail.visibility = View.VISIBLE
        currentBinding.btnDatePickerDetail.visibility = View.VISIBLE
        currentBinding.btnDatePickerDetail.isEnabled = true
        currentBinding.btnSaveDetail.visibility = View.VISIBLE
        currentBinding.btnHighlightDetail.visibility = View.VISIBLE

        val hideUnusedToolbarActions = isProfileEntry()
        currentBinding.btnToolbarAddDetail.visibility = if (hideUnusedToolbarActions) View.GONE else View.VISIBLE
        currentBinding.btnToolbarPenDetail.visibility = if (hideUnusedToolbarActions) View.GONE else View.VISIBLE
        currentBinding.btnToolbarTextDetail.visibility = if (hideUnusedToolbarActions) View.GONE else View.VISIBLE
        currentBinding.btnToolbarCommentDetail.visibility = View.VISIBLE
    }

    private fun diaryPageResource(): Int = when (ThemeManager.currentTheme(requireContext())) {
        AppTheme.ROSE -> R.drawable.diarypage
        AppTheme.SAGE -> R.drawable.diarypage_sage
        AppTheme.SKY -> R.drawable.diarypage_sky
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
            val defaultColor = if (originalColor != 0) originalColor else (buttonColorMap[button] ?: "#EDEDED".toColorInt())

            if (isSelected) {
                button.backgroundTintList = ColorStateList.valueOf("#FFF59D".toColorInt())
            } else {
                button.backgroundTintList = ColorStateList.valueOf(defaultColor)
            }
            button.invalidate()
            button.refreshDrawableState()
        }
    }

    private fun checkFollowStateAndLoad() {
        if (isMyDiary) {
            loadDiaryAndComments()
        } else {
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
                    isFollowingUser = followDoc.exists()
                    loadDiaryAndComments()
                } catch (e: Exception) {
                    Log.e("DiaryDetail", "팔로우 상태 확인 실패", e)
                    if (isAdded && _binding != null) {
                        isFollowingUser = false
                        loadDiaryAndComments()
                    }
                }
            }
        }
    }

    private fun setupSwipeGesture() {
        if (!canNavigateDate()) return
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
        if (!canNavigateDate()) return
        try {
            val parsedDate = dateFormat.parse(targetDate) ?: return
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                add(Calendar.DAY_OF_MONTH, amount)
            }
            targetDate = dateFormat.format(cal.time)

            updateTitleDateText()
            updateCommentButtonState()
            checkFollowStateAndLoad()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCommentButtonState() {
        val currentBinding = _binding ?: return
        if (isCurrentDateOrFuture() || isReadOnlyMode) {
            currentBinding.btnToolbarCommentDetail.isEnabled = false
            currentBinding.btnToolbarCommentDetail.alpha = 0.3f
        } else {
            currentBinding.btnToolbarCommentDetail.isEnabled = true
            currentBinding.btnToolbarCommentDetail.alpha = 1.0f
        }
    }

    private fun isCurrentDateOrFuture(): Boolean {
        val todayStr = dateFormat.format(Date())
        return targetDate >= todayStr
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
        if (!canNavigateDate()) return
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
                    updateCommentButtonState()
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
            val ownerKey = effectiveUidString.hashCode()

            var postIts = emptyList<DiaryEntity>()
            var comments = emptyList<CommentEntity>()

            try {
                val diaryQuerySnap = firestore.collection("diaries")
                    .whereEqualTo("userId", effectiveUidString)
                    .whereEqualTo("createdAt", targetDate)
                    .get()
                    .await()

                postIts = diaryQuerySnap.documents.mapNotNull { doc ->
                    try { doc.toObject(DiaryEntity::class.java) } catch (e: Exception) { null }
                }

                val commentQuerySnap = firestore.collection("comments")
                    .whereEqualTo("diaryId", ownerKey)
                    .whereEqualTo("date", targetDate)
                    .get()
                    .await()

                comments = commentQuerySnap.documents.mapNotNull { doc ->
                    try { doc.toObject(CommentEntity::class.java) } catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                Log.e("DiaryDetail", "Firestore 조회 실패, 로컬 DB로 대체합니다.", e)
            }

            try {
                val db = AppDatabase.getDatabase(safeContext)
                if (postIts.isEmpty()) {
                    postIts = withContext(Dispatchers.IO) {
                        db.diaryDao().getPostItsByDateAndUserId(targetDate, effectiveUidString)
                    }
                }
                if (comments.isEmpty()) {
                    comments = withContext(Dispatchers.IO) {
                        db.diaryDao().getCommentsByDate(targetDate)
                            .filter { comment ->
                                comment.diaryId == ownerKey || (comment.diaryId == 0 && comment.userId == effectiveUidString)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e("DiaryDetail", "로컬 DB 조회 실패", e)
            }

            val visibleComments = comments.filter { comment ->
                if (isMyDiary) true else comment.userId != effectiveUidString
            }

            val commentAuthorMap = withContext(Dispatchers.IO) {
                val labels = mutableMapOf<String, String>()
                val userIds = visibleComments.map { it.userId }.toSet()
                for (uid in userIds) {
                    labels[uid] = when {
                        uid == myUid -> "나"
                        uid == effectiveUidString && !isMyDiary -> "작성자"
                        else -> resolveUserNickname(uid)
                    }
                }
                labels
            }

            val binding = _binding ?: return@launch
            if (!isAdded) return@launch

            isPastHighlighted = postIts.any { it.isHighlighted }
            applyCustomButtonState(binding.btnHighlightDetail, isSelected = isPastHighlighted)

            currentPostIts = postIts
            binding.layoutDetailDiaryContainer.removeAllViews()

            val sortedPostIts = postIts.sortedBy { it.zIndex }
            for (postIt in sortedPostIts) {
                val isDecoText = postIt.content.startsWith("[DECO]:")
                if (isDecoText) {
                    val pureText = postIt.content.replace("[DECO]:", "")
                    renderReadOnlyDecoText(pureText, postIt.positionX, postIt.positionY)
                } else {
                    renderReadOnlyPostIt(postIt, isFollowingUser)
                }
            }

            val sortedComments = visibleComments.sortedBy { it.zIndex }

            val currentTime = System.currentTimeMillis()
            val twentyFourHoursInMs = 24 * 60 * 60 * 1000L

            fun parseCommentTime(timeStr: String): Long {
                return try {
                    val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                    sdf.parse(timeStr)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }

            fun resolveCreatedAt(comment: CommentEntity): Long {
                return if (comment.createdAt > 0L) comment.createdAt else parseCommentTime(comment.timestamp)
            }

            val selfComments = visibleComments.filter { isMyDiary && it.userId == myUid }
            val latestSelfCommentTime = selfComments.maxOfOrNull { resolveCreatedAt(it) } ?: 0L
            val hasRecentSelfComment = (currentTime - latestSelfCommentTime) < twentyFourHoursInMs
            val unreadEmojiComments = visibleComments.filter {
                isMyDiary &&
                        it.userId != myUid &&
                        isEmojiCommentContent(it.content) &&
                        !it.isChecked
            }

            binding.layoutCommentsContainer.removeAllViews()
            for (comment in sortedComments) {
                val isSelfComment = isMyDiary && (comment.userId == myUid)
                val commentTime = resolveCreatedAt(comment)
                val isOlderThan24Hours = (currentTime - commentTime) >= twentyFourHoursInMs

                val shouldBlur = isSelfComment && isOlderThan24Hours && !hasRecentSelfComment
                val shouldAnimateEmoji = unreadEmojiComments.any { it.commentId == comment.commentId }

                renderCommentPostIt(
                    comment = comment,
                    authorLabel = commentAuthorMap[comment.userId] ?: "알 수 없음",
                    isBlurred = shouldBlur,
                    shouldAnimateEmoji = shouldAnimateEmoji
                )
            }
      }

    }

    private fun markCommentsAsChecked(comments: List<CommentEntity>, ownerUid: String) {
        if (!isMyDiary || comments.isEmpty()) return

        val safeContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(safeContext)
                withContext(Dispatchers.IO) {
                    comments.forEach { comment ->
                        val checkedComment = comment.copy(isChecked = true)
                        db.diaryDao().insertComment(checkedComment)
                        try {
                            firestore.collection("comments")
                                .document("${ownerUid}_${comment.date}_${comment.commentId}")
                                .set(checkedComment, SetOptions.merge())
                                .await()
                        } catch (e: Exception) {
                            Log.e("DiaryDetail", "댓글 확인 상태 동기화 실패", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DiaryDetail", "댓글 확인 상태 저장 실패", e)
            }
        }
    }

    private suspend fun resolveUserNickname(userId: String): String {
        if (userId.isBlank()) return "알 수 없음"
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getString("nickname") ?: "알 수 없음"
        } catch (_: Exception) {
            "알 수 없음"
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

            translationX = posX.coerceAtLeast(0f)
            translationY = posY

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        currentBinding.layoutDetailDiaryContainer.addView(decorateTextView)
        decorateTextView.post { clampViewToParent(decorateTextView) }
    }

    private fun renderReadOnlyPostIt(diary: DiaryEntity, isCurrUserFollowing: Boolean = true) {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val shouldHide = !isMyDiary && !isCurrUserFollowing && diary.visibility == "비공개"
        val shouldBlur = !isMyDiary && !isCurrUserFollowing && diary.visibility == "팔로워공개"

        if (shouldHide) {
            return
        }

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
            applyHighlightRangesToEditText(etContent, diary.highlightRanges ?: "", shouldBlur)
        } else {
            etContent?.text = diary.content
        }

        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg?.setImageResource(resId)

        view.translationX = diary.positionX.coerceAtLeast(0f)
        view.translationY = diary.positionY

        if (shouldBlur && etContent is EditText) {
            etContent.alpha = 0.35f
            etContent.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        currentBinding.layoutDetailDiaryContainer.addView(view)
        view.post { clampViewToParent(view) }
    }

    private fun applyHighlightRangesToEditText(etContent: EditText, rangesStr: String, shouldBlur: Boolean = false) {
        if (rangesStr.isBlank()) return
        if (shouldBlur) return

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

    private fun formatRelativeDate(timestampStr: String, dateStr: String): String {
        val rawString = timestampStr.ifBlank { dateStr }
        if (rawString.isBlank()) return ""

        // 1. 단순 날짜만 온 경우 ("2026-08-11" 또는 "2026.08.11")
        val dateOnly = rawString.split(" ")[0].replace("-", ".")

        return try {
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val commentDate = sdf.parse(dateOnly) ?: return dateOnly

            // 시간 제거한 오늘 날짜 계산
            val calToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val calComment = Calendar.getInstance().apply {
                time = commentDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 날짜 차이(일 단위) 계산
            val diffDays = ((calToday.timeInMillis - calComment.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

            when (diffDays) {
                0 -> "오늘"
                1 -> "어제"
                in 2..7 -> "${diffDays}일 전"
                else -> dateOnly // 7일 지나면 2026.08.11 형태로 표시
            }
        } catch (_: Exception) {
            dateOnly
        }
    }

    private fun renderCommentPostIt(
        comment: CommentEntity,
        authorLabel: String,
        isBlurred: Boolean = false,
        shouldAnimateEmoji: Boolean = false
    ) {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_comment, currentBinding.layoutCommentsContainer, false)

        val ivCommentBg = view.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = view.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = view.findViewById<TextView>(R.id.tvCommentTime)
        val tvAuthor = view.findViewById<TextView>(R.id.tvCommentAuthor)
        val btnCommentDone = view.findViewById<TextView>(R.id.btnCommentDone)
        val tvEmoji = view.findViewById<TextView>(R.id.tvCommentEmoji)

        val layoutAuthor = view.findViewById<View>(R.id.layoutCommentAuthor)
        val ivProfile = view.findViewById<ImageView>(R.id.ivCommentProfile)

        val (emoji, plainText) = decodeCommentContent(comment.content)
        val isEmojiComment = isEmojiCommentContent(comment.content)

        val displayContent = if (plainText.isNotBlank()) plainText else emoji
        etCommentContent.setText(displayContent)

        tvTime.visibility = View.VISIBLE
        tvTime.text = formatRelativeDate(comment.date, comment.timestamp)
        tvAuthor.text = authorLabel

        layoutAuthor?.setOnClickListener {
            val commentUserId = comment.userId
            if (commentUserId.isNotBlank()) {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.animator.screen_morph_enter,
                        R.animator.screen_morph_exit,
                        R.animator.screen_morph_enter,
                        R.animator.screen_morph_exit
                    )
                    .replace(R.id.fragment_container, ProfileFragment.newInstance(commentUserId))
                    .addToBackStack(null)
                    .commit()
            }
        }

        if (isBlurred) {
            etCommentContent.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            etCommentContent.paint.maskFilter = android.graphics.BlurMaskFilter(
                16f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        } else {
            etCommentContent.setLayerType(View.LAYER_TYPE_NONE, null)
            etCommentContent.paint.maskFilter = null
        }

        val resId = commentResourceMap[comment.color] ?: R.drawable.comment_blue
        ivCommentBg.setImageResource(resId)

        view.setTag(R.id.ivCommentBg, comment.commentId)
        view.tag = comment.color
        view.setTag(R.id.btnFollow, comment.userId)
        view.setTag(R.id.btnProfileHome, if (isMyDiary) AuthUtils.getCurrentUserId() else (targetUserId ?: ""))
        view.setTag(R.id.tvCommentEmoji, comment.createdAt)
        view.setTag(R.id.btnCommentDone, comment.isChecked)

        view.translationX = comment.posX.coerceAtLeast(0f)
        view.translationY = comment.posY

        btnCommentDone.visibility = View.GONE

        if (isEmojiComment) {
            setupEmojiCommentToggle(
                commentView = view,
                comment = comment,
                emoji = emoji,
                startCollapsed = true
            )

            if (shouldAnimateEmoji) {
                startUnreadEmojiBounce(tvEmoji)
            }
        } else {
            tvEmoji.visibility = View.GONE
        }

        if (!isHighlightMode) {
            // 💡 [수정] 삭제 권한(내 일기장 OR 내가 단 댓글) 체크 후 더블탭 콜백 전달
            val myUid = auth.currentUser?.uid ?: ""
            val canDelete = isMyDiary || (comment.userId == myUid)

            makeViewDraggable(view) {
                if (canDelete) {
                    showCuteDeleteDialog(view, comment)
                } else {
                    Toast.makeText(safeContext, "다른 사람의 댓글은 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            lockCommentEditText(view, etCommentContent)
        }

        currentBinding.layoutCommentsContainer.addView(view)
        view.post { clampViewToParent(view) }
    }


    private fun insertCommentToDb(view: View, commentText: String, colorName: String, timestamp: String, zIndex: Int = 0) {
        val safeContext = context?.applicationContext ?: return
        val currentUid = AuthUtils.getCurrentUserId()
        val ownerUid = if (isMyDiary) currentUid else (targetUserId ?: currentUid)

        val posX = view.translationX
        val posY = view.translationY

        val existingCommentId = (view.getTag(R.id.ivCommentBg) as? Int) ?: 0

        val newComment = CommentEntity(
            commentId = existingCommentId,
            diaryId = ownerUid.hashCode(),
            userId = currentUid,
            date = targetDate,
            content = commentText,
            color = colorName,
            timestamp = timestamp,
            posX = posX,
            posY = posY,
            zIndex = zIndex
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val savedId = withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(safeContext)
                    db.diaryDao().insertComment(newComment)
                }

                val commentWithId = newComment.copy(commentId = savedId.toInt())
                firestore.collection("comments")
                    .document("${ownerUid}_${targetDate}_${savedId}")
                    .set(commentWithId)
                    .await()

                if (_binding == null || !isAdded) return@launch

                view.setTag(R.id.ivCommentBg, savedId.toInt())
                view.setTag(R.id.btnFollow, currentUid)
                view.setTag(R.id.btnProfileHome, ownerUid)
                view.setTag(R.id.tvCommentEmoji, newComment.createdAt)
                view.setTag(R.id.btnCommentDone, newComment.isChecked)
                Toast.makeText(safeContext, "댓글이 등록되었습니다. 🎉", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("DiaryDetail", "댓글 저장 실패", e)
            }
        }
    }

    private fun saveAllCommentsAndDiaryState() {
        if (isHighlightMode) return

        val safeContext = context?.applicationContext ?: return
        val currentBinding = _binding ?: return
        val currentUid = AuthUtils.getCurrentUserId()

        val container = currentBinding.layoutCommentsContainer
        val childCount = container.childCount

        val commentsToSave = mutableListOf<Pair<View, CommentEntity>>()

        for (i in 0 until childCount) {
            val commentView = container.getChildAt(i) ?: continue
            val etCommentContent = commentView.findViewById<EditText>(R.id.etCommentContent) ?: continue
            val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime) ?: continue
            val btnCommentDone = commentView.findViewById<TextView>(R.id.btnCommentDone)

            val text = etCommentContent.text.toString().trim()
            if (text.isNotBlank()) {

                if (btnCommentDone != null && btnCommentDone.isVisible) {
                    btnCommentDone.visibility = View.GONE
                    etCommentContent.clearFocus()
                    lockCommentEditText(commentView, etCommentContent)
                    makeViewDraggable(commentView)
                }

                val existingCommentId = (commentView.getTag(R.id.ivCommentBg) as? Int) ?: 0
                val colorName = (commentView.tag as? String) ?: "blue"
                val posX = commentView.translationX
                val posY = commentView.translationY
                val commentDate = tvTime.text.toString()
                val authorUid = (commentView.getTag(R.id.btnFollow) as? String).orEmpty().ifBlank { currentUid }
                val ownerUid = (commentView.getTag(R.id.btnProfileHome) as? String)
                    .orEmpty()
                    .ifBlank { if (isMyDiary) currentUid else (targetUserId ?: currentUid) }
                val createdAt = (commentView.getTag(R.id.tvCommentEmoji) as? Long) ?: System.currentTimeMillis()
                val isChecked = (commentView.getTag(R.id.btnCommentDone) as? Boolean) ?: false

                val updatedComment = CommentEntity(
                    commentId = existingCommentId,
                    diaryId = ownerUid.hashCode(),
                    userId = authorUid,
                    date = targetDate,
                    content = text,
                    color = colorName,
                    timestamp = commentDate,
                    createdAt = createdAt,
                    isChecked = isChecked,
                    posX = posX,
                    posY = posY,
                    zIndex = i
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

                    try {
                        firestore.collection("diaries")
                            .document("${currentUid}_${targetDate}_${updatedPostIt.diaryId}")
                            .set(updatedPostIt, SetOptions.merge())
                            .await()
                    } catch (e: Exception) {
                        Log.e("DiaryDetail", "Firestore 포스트잇 동기화 실패", e)
                    }
                }
            }

            if (commentsToSave.isNotEmpty()) {
                val savedResults = withContext(Dispatchers.IO) {
                    commentsToSave.map { (view, comment) ->
                        val savedId = db.diaryDao().insertComment(comment)
                        Pair(view, savedId.toInt())
                    }
                }
                if (isAdded && _binding != null) {
                    for ((view, savedId) in savedResults) {
                        view.setTag(R.id.ivCommentBg, savedId.toInt())
                    }
                }

                withContext(Dispatchers.IO) {
                    for ((view, savedId) in savedResults) {
                        val comment = commentsToSave.firstOrNull { it.first == view }?.second ?: continue
                        val commentWithId = comment.copy(commentId = savedId)
                        val ownerUid = if (isMyDiary) currentUid else (targetUserId ?: currentUid)

                        try {
                            firestore.collection("comments")
                                .document("${ownerUid}_${targetDate}_${savedId}")
                                .set(commentWithId, SetOptions.merge())
                                .await()
                        } catch (e: Exception) {
                            Log.e("DiaryDetail", "Firestore 댓글 동기화 실패", e)
                        }
                    }
                }

                if (isAdded && _binding != null) {
                    for ((view, savedId) in savedResults) {
                        view.setTag(R.id.ivCommentBg, savedId)
                        val comment = commentsToSave.firstOrNull { it.first == view }?.second
                        if (comment != null) {
                            view.setTag(R.id.tvCommentEmoji, comment.createdAt)
                        }
                    }
                }
            }

            if (isAdded && _binding != null) {
                Toast.makeText(safeContext, "저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeViewDraggable(view: View, onDoubleTap: (() -> Unit)? = null) {
        var lastX = 0f
        var lastY = 0f
        var startX = 0f
        var startY = 0f
        var isLongPressed = false
        var isMoved = false
        var lastClickTime = 0L

        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        val longPressRunnable = Runnable {
            isLongPressed = true

            view.bringToFront()
            view.parent?.requestLayout()
            view.invalidate()

            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(view.context, "맨 앞으로 가져왔습니다.", Toast.LENGTH_SHORT).show()
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    isLongPressed = false
                    isMoved = false

                    handler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    val totalDistance = hypot((event.rawX - startX).toDouble(), (event.rawY - startY).toDouble())
                    if (totalDistance > touchSlop) {
                        isMoved = true
                        handler.removeCallbacks(longPressRunnable)
                    }

                    moveViewWithinParent(v, dx, dy)

                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!isLongPressed && !isMoved && event.action == MotionEvent.ACTION_UP) {
                        val currentTime = System.currentTimeMillis()

                        // 💡 연속 두 번 클릭 처리
                        if (currentTime - lastClickTime < doubleTapTimeout) {
                            onDoubleTap?.invoke()
                            lastClickTime = 0L
                        } else {
                            lastClickTime = currentTime
                            v.performClick()
                        }
                    }
                }
            }
            true
        }
    }

    private fun showCuteDeleteDialog(commentView: View, comment: CommentEntity) {
        val safeContext = context ?: return
        val currentBinding = _binding ?: return

        val dialogView = LayoutInflater.from(safeContext).inflate(R.layout.dialog_delete_comment, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(safeContext)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDelete)

        // 💡 현재 앱의 Palette 테마 색상 자동 적용
        val accentColor = ContextCompat.getColor(safeContext, palette.accent)
        val strokeColor = ContextCompat.getColor(safeContext, palette.stroke)

        btnDelete.backgroundTintList = ColorStateList.valueOf(accentColor)
        btnCancel.backgroundTintList = ColorStateList.valueOf(strokeColor)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            // 1. 화면 컨테이너에서 포스트잇 제거
            currentBinding.layoutCommentsContainer.removeView(commentView)

            // 2. Firestore & Room DB 데이터 삭제
            deleteCommentFromDb(comment)

            Toast.makeText(safeContext, "댓글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun deleteCommentFromDb(comment: CommentEntity) {
        val safeContext = context?.applicationContext ?: return
        val currentUid = AuthUtils.getCurrentUserId()
        val ownerUid = if (isMyDiary) currentUid else (targetUserId ?: currentUid)

        // Firestore 문서 키 구조: ${ownerUid}_${date}_${commentId}
        val docId = "${ownerUid}_${comment.date}_${comment.commentId}"

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Firestore DB 삭제
            try {
                firestore.collection("comments")
                    .document(docId)
                    .delete()
                    .await()
                Log.d("DiaryDetail", "Firestore 댓글 삭제 완료: $docId")
            } catch (e: Exception) {
                Log.e("DiaryDetail", "Firestore 댓글 삭제 실패", e)
            }

            // 2. Room 로컬 DB 삭제
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(safeContext)
                    db.diaryDao().deleteComment(comment)
                }
                Log.d("DiaryDetail", "Room 댓글 삭제 완료: ${comment.commentId}")
            } catch (e: Exception) {
                Log.e("DiaryDetail", "로컬 DB 댓글 삭제 실패", e)
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun lockCommentEditText(commentView: View, etCommentContent: EditText) {
        etCommentContent.keyListener = null
        etCommentContent.movementMethod = null      // 💡 [필수 1] 텍스트 선택/커서 터치 이벤트 제거
        etCommentContent.isCursorVisible = false
        etCommentContent.clearFocus()

        etCommentContent.isEnabled = true
        etCommentContent.isClickable = false
        etCommentContent.isLongClickable = false  // 💡 [필수 2] 롱클릭 이벤트 소비 방지
        etCommentContent.isFocusable = false
        etCommentContent.isFocusableInTouchMode = false

        // 💡 [핵심] false를 반환하여 텍스트박스를 터치해도 부모(commentView)가 터치, 드래그, 롱클릭을 모두 처리하도록 통과시킴
        etCommentContent.setOnTouchListener { _, _ -> false }
    }

    private fun limitEditTextToPostItBounds(editText: EditText, maxLines: Int = 3) {
        editText.maxLines = maxLines

        editText.addTextChangedListener(object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // 입력 직전의 텍스트 백업
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // 레이아웃이 계산된 후 줄 수가 최대 허용 줄 수를 초과하면 이전 글자로 원복
                if (editText.layout != null && editText.layout.lineCount > maxLines) {
                    editText.setText(previousText)
                    // 커서를 맨 뒤로 이동
                    editText.setSelection(editText.text.length)
                    Toast.makeText(editText.context, "포스트잇 범위를 넘어 입력할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun moveViewWithinParent(view: View, dx: Float, dy: Float) {
        val diaryPage = _binding?.ivFixedDiaryPageDetail ?: return
        DiaryPageBounds.move(view, diaryPage, dx, dy)
    }

    private fun clampViewToParent(view: View) {
        val diaryPage = _binding?.ivFixedDiaryPageDetail ?: return
        DiaryPageBounds.clamp(view, diaryPage)
    }

    private fun isEmojiCommentContent(raw: String): Boolean = raw.contains(COMMENT_SEPARATOR)

    private fun decodeCommentContent(raw: String): Pair<String, String> {
        val splitIndex = raw.indexOf(COMMENT_SEPARATOR)
        if (splitIndex <= 0) return Pair(DEFAULT_COMMENT_EMOJI, raw)

        val emoji = raw.substring(0, splitIndex).ifBlank { DEFAULT_COMMENT_EMOJI }
        val text = raw.substring(splitIndex + COMMENT_SEPARATOR.length)
        return Pair(emoji, text)
    }


    private fun setupEmojiCommentToggle(
        commentView: View,
        comment: CommentEntity,
        emoji: String,
        startCollapsed: Boolean
    ) {
        val ivCommentBg = commentView.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = commentView.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime)
        val tvAuthor = commentView.findViewById<TextView>(R.id.tvCommentAuthor)
        val tvEmoji = commentView.findViewById<TextView>(R.id.tvCommentEmoji)
        val btnCommentDone = commentView.findViewById<TextView>(R.id.btnCommentDone)

        tvEmoji.text = emoji
        btnCommentDone.visibility = View.GONE

        fun setCollapsed(collapsed: Boolean) {
            commentView.setTag(R.id.btnToolbarCommentDetail, !collapsed)
            ivCommentBg.visibility = if (collapsed) View.GONE else View.VISIBLE
            etCommentContent.visibility = if (collapsed) View.GONE else View.VISIBLE
            tvTime.visibility = if (collapsed) View.GONE else View.VISIBLE
            tvAuthor.visibility = if (collapsed) View.GONE else View.VISIBLE
            tvEmoji.visibility = if (collapsed) View.VISIBLE else View.GONE
        }

        val toggleClick = View.OnClickListener {
            // 1. 애니메이션 정지 및 원래 크기 복원
            stopEmojiBounce(tvEmoji)

            // 2. 안 읽은 댓글인 경우 읽음 처리 (DB 및 메모리 태그)
            val isChecked = (commentView.getTag(R.id.btnCommentDone) as? Boolean) ?: comment.isChecked
            if (!isChecked) {
                commentView.setTag(R.id.btnCommentDone, true)
                // ❌ comment.isChecked = true   <- 이 줄을 제거했습니다!

                val myUid = AuthUtils.getCurrentUserId()
                val effectiveUid = if (isMyDiary) myUid else (targetUserId ?: myUid)
                markCommentsAsChecked(listOf(comment), effectiveUid)
            }

            // 3. 접힘/펼침 상태 토글
            val expanded = (commentView.getTag(R.id.btnToolbarCommentDetail) as? Boolean) ?: false
            setCollapsed(expanded)
        }

        commentView.setOnClickListener(toggleClick)
        tvEmoji.setOnClickListener(toggleClick)
        ivCommentBg.setOnClickListener(toggleClick)
        etCommentContent.setOnClickListener(toggleClick)
        tvTime.setOnClickListener(toggleClick)

        setCollapsed(startCollapsed)
    }

    private fun startUnreadEmojiBounce(target: View) {
        stopEmojiBounce(target) // 이전 애니메이션 제거
        ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -12f, 0f)
        ).apply {
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopEmojiBounce(tvEmoji: View) {
        tvEmoji.animate().cancel()
        tvEmoji.clearAnimation()
        tvEmoji.scaleX = 1f
        tvEmoji.scaleY = 1f
        tvEmoji.translationY = 0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
