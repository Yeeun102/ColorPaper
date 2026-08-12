package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.repository.UserRepository
import com.example.colorpaper.databinding.FragmentFriendDiaryDetailBinding
import com.example.colorpaper.ui.calendar.RecordDatePickerDialog
import com.example.colorpaper.ui.theme.AppTheme
import com.example.colorpaper.ui.theme.ThemeManager
import com.example.colorpaper.util.AuthUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FriendDiaryDetailFragment : Fragment() {

    private var _binding: FragmentFriendDiaryDetailBinding? = null
    private val binding get() = _binding

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

    private var targetUserId: String? = null
    private var diaryId: Int = 0
    private var selectedDate: String = "" // "yyyy-MM-dd" 형식
    private var isHighlightMode: Boolean = false

    private val calendar = Calendar.getInstance()
    private val dateFormatFull = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatDisplay = SimpleDateFormat("M월 d일", Locale.KOREA)
    private val dateFormatCheck = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var gestureDetector: GestureDetector
    private var isFollowingUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            targetUserId = it.getString("targetUserId")
            diaryId = it.getInt("diaryId", 0)
            selectedDate = it.getString("targetDate") ?: dateFormatFull.format(Date())
            isHighlightMode = it.getBoolean("isHighlightMode", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendDiaryDetailBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = _binding ?: return
        binding.ivFixedFriendDiaryPage.setImageResource(friendDiaryPageResource())

        // 1. 뒤로가기
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        applyEntryModeUi()

        // 2. 상단 날짜 및 달력 클릭 시 날짜 변경 (DatePicker)
        if (canNavigateDate()) {
            val dateClickListener = View.OnClickListener { showDatePickerDialog() }
            binding.tvHeaderDate.setOnClickListener(dateClickListener)
            binding.btnCalendar.setOnClickListener(dateClickListener)

            // 3. 제스처 설정 (좌우 스와이프 날짜 이동)
            setupSwipeGesture()
            binding.root.setOnTouchListener { _, event ->
                if (::gestureDetector.isInitialized) {
                    gestureDetector.onTouchEvent(event)
                }
                true
            }
        } else {
            binding.tvHeaderDate.setOnClickListener(null)
            binding.btnCalendar.setOnClickListener(null)
            binding.root.setOnTouchListener(null)
        }

        // 4. 프로필 홈 및 팔로우 버튼
        binding.btnProfileHome.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnFollow.setOnClickListener {
            toggleFollowStatus()
        }

        // 5. 하단 액션 버튼
        binding.btnFriendComment.setOnClickListener {
            if (!isFollowingUser) {
                Toast.makeText(context, "팔로우한 사용자에게만 댓글을 작성할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val todayStr = dateFormatCheck.format(Date())
            val isCurrentDateOrFuture = selectedDate >= todayStr

            if (isCurrentDateOrFuture) {
                Toast.makeText(context, "오늘 이후의 일기에는 댓글을 작성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                showAddCommentDialog()
            }
        }

        val todayStr = dateFormatCheck.format(Date())
        val isCurrentDateOrFuture = selectedDate >= todayStr
        if (isCurrentDateOrFuture) {
            binding.btnFriendComment.alpha = 0.3f
        } else {
            binding.btnFriendComment.alpha = 1.0f
        }

        // 6. 최초 데이터 로드
        updateDateTextDisplay()
        loadFriendDiaryData()
    }

    private fun canNavigateDate(): Boolean = !isHighlightMode

    private fun applyEntryModeUi() {
        val binding = _binding ?: return
        binding.btnCalendar.visibility = if (isHighlightMode) View.GONE else View.VISIBLE
        binding.btnCalendar.isEnabled = !isHighlightMode
        binding.tvHeaderDate.isClickable = !isHighlightMode
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
            val parsedDate = dateFormatFull.parse(selectedDate) ?: return
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                add(Calendar.DAY_OF_MONTH, amount)
            }
            selectedDate = dateFormatFull.format(cal.time)
            updateDateTextDisplay()
            updateCommentButtonState()
            loadFriendDiaryData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCommentButtonState() {
        val binding = _binding ?: return

        if (!isFollowingUser) {
            binding.btnFriendComment.alpha = 0.3f
            return
        }

        val todayStr = dateFormatCheck.format(Date())
        val isCurrentDateOrFuture = selectedDate >= todayStr
        if (isCurrentDateOrFuture) {
            binding.btnFriendComment.alpha = 0.3f
        } else {
            binding.btnFriendComment.alpha = 1.0f
        }
    }

    private fun updateDateTextDisplay() {
        val binding = _binding ?: return
        try {
            val parsedDate = dateFormatFull.parse(selectedDate)
            if (parsedDate != null) {
                binding.tvHeaderDate.text = dateFormatDisplay.format(parsedDate)
            } else {
                binding.tvHeaderDate.text = selectedDate
            }
        } catch (_: Exception) {
            binding.tvHeaderDate.text = selectedDate
        }
    }

    private fun showDatePickerDialog() {
        if (!canNavigateDate()) return
        val uid = targetUserId ?: return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val recordedDates = withContext(Dispatchers.IO) {
                UserRepository(AppDatabase.getDatabase(appContext))
                    .getPublicDiariesByUserId(uid)
                    .map { it.createdAt }
                    .toSet()
            }
            if (!isAdded) return@launch
            RecordDatePickerDialog.show(requireContext(), selectedDate, recordedDates) { dateKey ->
                selectedDate = dateKey
                updateDateTextDisplay()
                updateCommentButtonState()
                loadFriendDiaryData()
            }
        }
    }

    private fun loadFriendDiaryData() {
        val uid = targetUserId ?: return
        val safeContext = context ?: return
        val myUid = auth.currentUser?.uid ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val followDoc = firestore.collection("users")
                    .document(myUid)
                    .collection("following")
                    .document(uid)
                    .get()
                    .await()
                isFollowingUser = followDoc.exists()

                // 1) 유저 정보 조회
                val userDoc = firestore.collection("users").document(uid).get().await()
                val nickname = userDoc.getString("nickname") ?: userDoc.getString("name") ?: "친구"
                val profileImg = userDoc.getString("profileImageUrl")

                // 2) 선택된 날짜의 포스트잇 가져오기 (Firestore)
                var postIts = emptyList<DiaryEntity>()
                var comments = emptyList<CommentEntity>()
                try {
                    val querySnap = firestore.collection("diaries")
                        .whereEqualTo("userId", uid)
                        .whereEqualTo("createdAt", selectedDate)
                        .get()
                        .await()

                    postIts = querySnap.documents.mapNotNull { doc ->
                        try { doc.toObject(DiaryEntity::class.java) } catch (e: Exception) { null }
                    }

                    val commentQuerySnap = firestore.collection("comments")
                        .whereEqualTo("diaryId", uid.hashCode())
                        .whereEqualTo("date", selectedDate)
                        .get()
                        .await()

                    comments = commentQuerySnap.documents.mapNotNull { doc ->
                        try { doc.toObject(CommentEntity::class.java) } catch (e: Exception) { null }
                    }
                } catch (e: Exception) {
                    Log.e("FriendDiaryDetail", "Firestore 조회 실패: ${e.message}")
                }

                // Room DB Fallback
                val db = AppDatabase.getDatabase(safeContext.applicationContext)
                if (postIts.isEmpty()) {
                    postIts = db.diaryDao().getPostItsByDateAndUserId(selectedDate, uid)
                }

                // 3) 댓글 정보 가져오기 (소유자 기준: diaryId=ownerUid.hashCode)
                val ownerKey = uid.hashCode()
                if (comments.isEmpty()) {
                    comments = db.diaryDao().getCommentsByDate(selectedDate)
                        .filter { comment ->
                            comment.diaryId == ownerKey || (comment.diaryId == 0 && comment.userId == uid)
                        }
                }

                val visibleComments = comments.filter { comment ->
                    comment.userId != uid
                }

                val commentAuthorMap = mutableMapOf<String, String>()
                val commentUserIds = visibleComments.map { it.userId }.toSet()
                for (commentUserId in commentUserIds) {
                    commentAuthorMap[commentUserId] = when {
                        commentUserId == myUid -> "나"
                        commentUserId == uid -> "작성자"
                        else -> resolveUserNickname(commentUserId)
                    }
                }

                withContext(Dispatchers.Main) {
                    val binding = _binding ?: return@withContext
                    if (!isAdded) return@withContext

                    refreshFollowButtonUi()
                    updateCommentButtonState()

                    binding.tvFriendNickname.text = nickname
                    binding.ivFriendProfile.setImageResource(R.drawable.ic_default_profile)

                    // 4) 캔버스 초기화 후 그려주기
                    binding.layoutDetailDiaryContainer.removeAllViews()
                    binding.layoutCommentsContainer.removeAllViews()

                    if (postIts.isEmpty()) {
                        Toast.makeText(context, "해당 날짜에 작성된 일기가 없습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        for (postIt in postIts) {
                            if (postIt.content.startsWith("[DECO]:")) {
                                val pureText = postIt.content.replace("[DECO]:", "")
                                renderReadOnlyDecoText(pureText, postIt.positionX, postIt.positionY)
                            } else {
                                renderReadOnlyPostIt(postIt, isFollowingUser)
                            }
                        }
                    }

                    // 5) 댓글 그려주기
                    for (comment in visibleComments) {
                        renderCommentPostIt(comment, commentAuthorMap[comment.userId] ?: "알 수 없음")
                    }
                }
            } catch (e: Exception) {
                Log.e("FriendDiaryDetail", "친구 일기 로드 실패: ${e.message}")
            }
        }
    }

    private fun refreshFollowButtonUi() {
        val binding = _binding ?: return
        binding.btnFollow.text = if (isFollowingUser) "팔로우 중" else "팔로우"
    }

    private fun toggleFollowStatus() {
        val myUid = auth.currentUser?.uid ?: return
        val friendUid = targetUserId ?: return
        val safeContext = context ?: return

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val myFollowingRef = firestore.collection("users")
                        .document(myUid)
                        .collection("following")
                        .document(friendUid)

                    val friendFollowerRef = firestore.collection("users")
                        .document(friendUid)
                        .collection("followers")
                        .document(myUid)

                    val existing = myFollowingRef.get().await()
                    if (existing.exists()) {
                        myFollowingRef.delete().await()
                        friendFollowerRef.delete().await()
                        isFollowingUser = false
                    } else {
                        val data = mapOf("createdAt" to System.currentTimeMillis())
                        myFollowingRef.set(data).await()
                        friendFollowerRef.set(data).await()
                        isFollowingUser = true
                    }
                }

                if (!isAdded || _binding == null) return@launch
                refreshFollowButtonUi()
                updateCommentButtonState()
                Toast.makeText(
                    safeContext,
                    if (isFollowingUser) "팔로우했습니다." else "팔로우를 취소했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                loadFriendDiaryData()
            } catch (e: Exception) {
                if (!isAdded || _binding == null) return@launch
                Toast.makeText(safeContext, "팔로우 상태를 변경하지 못했습니다.", Toast.LENGTH_SHORT).show()
                Log.e("FriendDiaryDetail", "toggleFollowStatus failed", e)
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

    private fun renderReadOnlyPostIt(diary: DiaryEntity, isCurrUserFollowing: Boolean = false) {
        val safeContext = context ?: return
        val binding = _binding ?: return

        val shouldHide = !isCurrUserFollowing && diary.visibility == "비공개"
        val shouldBlur = !isCurrUserFollowing && diary.visibility == "팔로워공개"

        if (shouldHide) {
            return
        }

        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_postit, binding.layoutDetailDiaryContainer, false)

        val ivBg = view.findViewById<ImageView>(R.id.ivPostItBg)
        val tvDate = view.findViewById<TextView>(R.id.tvPostItDate)
        val etContent = view.findViewById<TextView>(R.id.etPostItContent)

        tvDate?.text = diary.createdAt

        if (etContent is EditText) {
            etContent.setText(diary.content)
            etContent.isEnabled = false
            etContent.isFocusable = false
            applyHighlightRangesToEditText(etContent, diary.highlightRanges, shouldBlur)
            if (shouldBlur) {
                etContent.alpha = 0.35f
                etContent.transformationMethod = PasswordTransformationMethod.getInstance()
            }
        } else {
            etContent?.text = diary.content
        }

        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg?.setImageResource(resId)

        // 마이페이지 다이어리와 100% 동일한 좌표 대입
        view.translationX = diary.positionX
        view.translationY = diary.positionY

        binding.layoutDetailDiaryContainer.addView(view)
        view.post { DiaryPageBounds.clamp(view, binding.ivFixedFriendDiaryPage) }
    }

    private fun renderReadOnlyDecoText(textStr: String, posX: Float, posY: Float) {
        val safeContext = context ?: return
        val binding = _binding ?: return
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

        binding.layoutDetailDiaryContainer.addView(decorateTextView)
        decorateTextView.post {
            DiaryPageBounds.clamp(decorateTextView, binding.ivFixedFriendDiaryPage)
        }
    }

    private fun renderCommentPostIt(comment: CommentEntity, authorLabel: String) {
        val safeContext = context ?: return
        val binding = _binding ?: return
        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

        val ivCommentBg = view.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = view.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = view.findViewById<TextView>(R.id.tvCommentTime)
        val tvAuthor = view.findViewById<TextView>(R.id.tvCommentAuthor)
        val btnCommentDone = view.findViewById<TextView>(R.id.btnCommentDone)
        val tvEmoji = view.findViewById<TextView>(R.id.tvCommentEmoji)

        val (emoji, plainText) = decodeCommentContent(comment.content)
        etCommentContent.setText(plainText)
        tvEmoji.text = emoji
        tvTime.text = comment.timestamp.ifBlank { comment.date }
        tvAuthor.text = "작성자 : $authorLabel"

        val resId = commentResourceMap[comment.color] ?: R.drawable.comment_blue
        ivCommentBg.setImageResource(resId)

        view.setTag(R.id.ivCommentBg, comment.commentId)
        view.tag = comment.color
        view.setTag(R.id.btnFollow, comment.userId)
        view.setTag(R.id.btnProfileHome, targetUserId ?: "")
        view.setTag(R.id.tvCommentEmoji, comment.createdAt)
        view.setTag(R.id.btnCommentDone, comment.isChecked)

        view.translationX = comment.posX
        view.translationY = comment.posY

        etCommentContent.isEnabled = false
        etCommentContent.isFocusable = false
        btnCommentDone.visibility = View.GONE

        setupCommentToggle(view, emoji, startCollapsed = true)
        enableDragAndScale(view)
        binding.layoutCommentsContainer.addView(view)
        view.post { DiaryPageBounds.clamp(view, binding.ivFixedFriendDiaryPage) }
    }

    private fun showAddCommentDialog() {
        val safeContext = context ?: return

        val emojiInput = EditText(safeContext).apply {
            hint = "표시 이모지 (예: 😀)"
            setSingleLine()
            setText(DEFAULT_COMMENT_EMOJI)
        }
        val contentInput = EditText(safeContext).apply {
            hint = "내용"
            setSingleLine(false)
            minLines = 3
        }

        val container = LinearLayout(safeContext).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(emojiInput)
            addView(contentInput)
        }

        AlertDialog.Builder(safeContext)
            .setTitle("댓글 추가")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("등록") { _, _ ->
                val emoji = emojiInput.text.toString().trim().ifBlank { DEFAULT_COMMENT_EMOJI }
                val text = contentInput.text.toString().trim()
                if (text.isBlank()) {
                    Toast.makeText(safeContext, "내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                addNewCommentPostIt(emoji, text)
            }
            .show()
    }

    private fun addNewCommentPostIt(emoji: String, text: String) {
        val safeContext = context ?: return
        val binding = _binding ?: return
        val randomColor = listOf("orange", "yellow", "green", "blue").random()

        val inflater = LayoutInflater.from(safeContext)
        val commentView = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

        val ivCommentBg = commentView.findViewById<ImageView>(R.id.ivCommentBg)
        val resId = commentResourceMap[randomColor] ?: R.drawable.comment_blue
        ivCommentBg.setImageResource(resId)

        val etCommentContent = commentView.findViewById<EditText>(R.id.etCommentContent)
        val btnCommentDone = commentView.findViewById<TextView>(R.id.btnCommentDone)
        val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime)
        val tvAuthor = commentView.findViewById<TextView>(R.id.tvCommentAuthor)
        val tvEmoji = commentView.findViewById<TextView>(R.id.tvCommentEmoji)

        val todayDateStr = dateFormatFull.format(Date())
        tvTime.text = todayDateStr
        tvAuthor.text = "작성자 : 나"
        commentView.tag = randomColor
        val currentUid = auth.currentUser?.uid ?: ""
        val ownerUid = targetUserId ?: ""
        commentView.setTag(R.id.btnFollow, currentUid)
        commentView.setTag(R.id.btnProfileHome, ownerUid)

        etCommentContent.setText(text)
        etCommentContent.isEnabled = false
        etCommentContent.isFocusable = false
        btnCommentDone.visibility = View.GONE
        tvEmoji.text = emoji

        setupCommentToggle(commentView, emoji, startCollapsed = true)
        enableDragAndScale(commentView)
        saveNewCommentToDb(commentView, emoji, text, randomColor, todayDateStr)

        binding.layoutCommentsContainer.addView(commentView)
        commentView.post { DiaryPageBounds.clamp(commentView, binding.ivFixedFriendDiaryPage) }
        commentView.bringToFront()
    }

    private fun saveNewCommentToDb(view: View, emoji: String, text: String, color: String, timestamp: String) {
        val safeContext = context?.applicationContext ?: return
        val ownerUid = targetUserId ?: return
        val currentUid = auth.currentUser?.uid ?: ownerUid

        val newComment = CommentEntity(
            diaryId = ownerUid.hashCode(),
            userId = currentUid,
            date = selectedDate,
            content = encodeCommentContent(emoji, text),
            color = color,
            timestamp = timestamp,
            posX = view.translationX,
            posY = view.translationY
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(safeContext)
            val savedId = db.diaryDao().insertComment(newComment)
            val commentWithId = newComment.copy(commentId = savedId.toInt())

            try {
                firestore.collection("comments")
                    .document("${ownerUid}_${selectedDate}_${savedId}")
                    .set(commentWithId, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("FriendDiaryDetail", "Firestore 댓글 저장 실패", e)
            }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    view.setTag(R.id.ivCommentBg, savedId.toInt())
                    view.setTag(R.id.btnFollow, currentUid)
                    view.setTag(R.id.btnProfileHome, ownerUid)
                    view.setTag(R.id.tvCommentEmoji, newComment.createdAt)
                    view.setTag(R.id.btnCommentDone, newComment.isChecked)
                    Toast.makeText(safeContext, "댓글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun persistCommentPosition(commentView: View) {
        val safeContext = context?.applicationContext ?: return
        val commentId = (commentView.getTag(R.id.ivCommentBg) as? Int) ?: return
        if (commentId <= 0) return

        val color = (commentView.tag as? String) ?: "blue"
        val etContent = commentView.findViewById<EditText>(R.id.etCommentContent)
        val tvEmoji = commentView.findViewById<TextView>(R.id.tvCommentEmoji)
        val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime)
        val createdAt = (commentView.getTag(R.id.tvCommentEmoji) as? Long) ?: System.currentTimeMillis()
        val isChecked = (commentView.getTag(R.id.btnCommentDone) as? Boolean) ?: false

        val authorUid = (commentView.getTag(R.id.btnFollow) as? String).orEmpty()
        val ownerUid = (commentView.getTag(R.id.btnProfileHome) as? String).orEmpty()
        val effectiveOwnerUid = if (ownerUid.isBlank()) (targetUserId ?: "") else ownerUid
        val effectiveAuthorUid = if (authorUid.isBlank()) (auth.currentUser?.uid ?: effectiveOwnerUid) else authorUid

        val payload = encodeCommentContent(
            tvEmoji.text?.toString().orEmpty().ifBlank { DEFAULT_COMMENT_EMOJI },
            etContent.text?.toString().orEmpty()
        )

        val updated = CommentEntity(
            commentId = commentId,
            diaryId = effectiveOwnerUid.hashCode(),
            userId = effectiveAuthorUid,
            date = selectedDate,
            content = payload,
            color = color,
            timestamp = tvTime.text?.toString().orEmpty(),
            createdAt = createdAt,
            isChecked = isChecked,
            posX = commentView.translationX,
            posY = commentView.translationY
        )

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(safeContext).diaryDao().insertComment(updated)
            try {
                firestore.collection("comments")
                    .document("${effectiveOwnerUid}_${selectedDate}_${commentId}")
                    .set(updated, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("FriendDiaryDetail", "Firestore 댓글 위치 저장 실패", e)
            }
        }
    }

    private fun encodeCommentContent(emoji: String, content: String): String {
        val safeEmoji = emoji.replace(COMMENT_SEPARATOR, "")
        return "$safeEmoji$COMMENT_SEPARATOR$content"
    }

    private fun decodeCommentContent(raw: String): Pair<String, String> {
        val splitIndex = raw.indexOf(COMMENT_SEPARATOR)
        if (splitIndex <= 0) return Pair(DEFAULT_COMMENT_EMOJI, raw)

        val emoji = raw.substring(0, splitIndex).ifBlank { DEFAULT_COMMENT_EMOJI }
        val text = raw.substring(splitIndex + COMMENT_SEPARATOR.length)
        return Pair(emoji, text)
    }

    private fun setupCommentToggle(commentView: View, emoji: String, startCollapsed: Boolean) {
        val ivCommentBg = commentView.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = commentView.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = commentView.findViewById<TextView>(R.id.tvCommentTime)
        val tvAuthor = commentView.findViewById<TextView>(R.id.tvCommentAuthor)
        val tvEmoji = commentView.findViewById<TextView>(R.id.tvCommentEmoji)
        val btnCommentDone = commentView.findViewById<TextView>(R.id.btnCommentDone)

        tvEmoji.text = emoji
        btnCommentDone.visibility = View.GONE

        fun setCollapsed(collapsed: Boolean) {
            commentView.setTag(R.id.btnFriendComment, !collapsed)
            ivCommentBg.visibility = if (collapsed) View.GONE else View.VISIBLE
            etCommentContent.visibility = if (collapsed) View.GONE else View.VISIBLE
            tvTime.visibility = if (collapsed) View.GONE else View.VISIBLE
            tvAuthor.visibility = if (collapsed) View.GONE else View.VISIBLE
            tvEmoji.visibility = if (collapsed) View.VISIBLE else View.GONE
        }

        val toggleClick = View.OnClickListener {
            val expanded = (commentView.getTag(R.id.btnFriendComment) as? Boolean) ?: false
            setCollapsed(expanded)
        }

        commentView.setOnClickListener(toggleClick)
        tvEmoji.setOnClickListener(toggleClick)
        ivCommentBg.setOnClickListener(toggleClick)
        etCommentContent.setOnClickListener(toggleClick)
        tvTime.setOnClickListener(toggleClick)

        setCollapsed(startCollapsed)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDragAndScale(view: View) {
        val safeContext = context ?: return
        val diaryPage = _binding?.ivFixedFriendDiaryPage ?: return
        var lastX = 0f
        var lastY = 0f
        var moved = false

        val scaleDetector = ScaleGestureDetector(safeContext, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val tvEmoji = view.findViewById<TextView>(R.id.tvCommentEmoji)
                val ivCommentBg = view.findViewById<ImageView>(R.id.ivCommentBg)
                if (tvEmoji.visibility != View.VISIBLE || ivCommentBg.visibility == View.VISIBLE) {
                    return false
                }

                val nextScale = (tvEmoji.scaleX * detector.scaleFactor).coerceIn(MIN_COMMENT_SCALE, MAX_COMMENT_SCALE)
                tvEmoji.scaleX = nextScale
                tvEmoji.scaleY = nextScale
                return true
            }
        })

        val dragAndScaleTouchListener = View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount > 1 || scaleDetector.isInProgress) return@OnTouchListener true

                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    if (kotlin.math.abs(dx) > 2f || kotlin.math.abs(dy) > 2f) {
                        moved = true
                    }

                    DiaryPageBounds.move(view, diaryPage, dx, dy)

                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        persistCommentPosition(view)
                    }
                    if (!moved) {
                        view.performClick()
                    }
                }
            }
            true
        }

        view.setOnTouchListener(dragAndScaleTouchListener)
        view.findViewById<View>(R.id.ivCommentBg)?.setOnTouchListener(dragAndScaleTouchListener)
        view.findViewById<View>(R.id.etCommentContent)?.setOnTouchListener(dragAndScaleTouchListener)
        view.findViewById<View>(R.id.tvCommentTime)?.setOnTouchListener(dragAndScaleTouchListener)
        view.findViewById<View>(R.id.tvCommentAuthor)?.setOnTouchListener(dragAndScaleTouchListener)
        view.findViewById<View>(R.id.tvCommentEmoji)?.setOnTouchListener(dragAndScaleTouchListener)
    }

    private fun applyHighlightRangesToEditText(etContent: EditText, rangesStr: String?, shouldBlur: Boolean = false) {
        if (rangesStr.isNullOrBlank()) return

        if (shouldBlur) return

        val text = etContent.text.toString()
        val spannable = SpannableString(text)
        val pairs = rangesStr?.split(",") ?: return

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

    private fun friendDiaryPageResource(): Int = when (ThemeManager.currentTheme(requireContext())) {
        AppTheme.ROSE -> R.drawable.diarypage
        AppTheme.SAGE -> R.drawable.diarypage_sage
        AppTheme.SKY -> R.drawable.diarypage_sky
    }

    override fun onResume() {
        super.onResume()
        (activity as? com.example.colorpaper.MainActivity)?.setBottomNavVisibility(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val COMMENT_SEPARATOR = "||"
        private const val DEFAULT_COMMENT_EMOJI = "💬"
        private const val MIN_COMMENT_SCALE = 0.7f
        private const val MAX_COMMENT_SCALE = 1.8f

        fun newInstance(
            targetUserId: String,
            diaryId: Int = 0,
            targetDate: String? = null,
            isHighlightMode: Boolean = false
        ) =
            FriendDiaryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("targetUserId", targetUserId)
                    putInt("diaryId", diaryId)
                    putString("targetDate", targetDate)
                    putBoolean("isHighlightMode", isHighlightMode)
                }
            }
    }

    private fun parseCommentTime(timeStr: String): Long {
        if (timeStr.isBlank()) return System.currentTimeMillis()
        timeStr.toLongOrNull()?.let { return it }

        val formats = listOf(
            "yyyy.MM.dd HH:mm",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy.MM.dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val parsed = sdf.parse(timeStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}
