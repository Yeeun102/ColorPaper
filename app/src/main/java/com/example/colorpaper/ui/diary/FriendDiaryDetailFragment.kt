package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.app.DatePickerDialog
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.databinding.FragmentFriendDiaryDetailBinding
import com.example.colorpaper.util.AuthUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private val calendar = Calendar.getInstance()
    private val dateFormatFull = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatDisplay = SimpleDateFormat("M월 d일", Locale.KOREA)

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            targetUserId = it.getString("targetUserId")
            diaryId = it.getInt("diaryId", 0)
            selectedDate = it.getString("targetDate") ?: dateFormatFull.format(Date())
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

        // 1. 뒤로가기
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. 상단 날짜 및 달력 클릭 시 날짜 변경 (DatePicker)
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

        // 4. 프로필 홈 및 팔로우 버튼
        binding.btnProfileHome.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnFollow.setOnClickListener {
            Toast.makeText(context, "팔로우 상태가 변경되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // 5. 하단 액션 버튼
        binding.btnFriendComment.setOnClickListener {
            addNewCommentPostIt()
        }
        binding.btnFriendStamp.setOnClickListener {
            Toast.makeText(context, "스탬프 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
        binding.btnFriendText.setOnClickListener {
            Toast.makeText(context, "텍스트 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        // 6. 최초 데이터 로드
        updateDateTextDisplay()
        loadFriendDiaryData()
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
            val parsedDate = dateFormatFull.parse(selectedDate) ?: return
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                add(Calendar.DAY_OF_MONTH, amount)
            }
            selectedDate = dateFormatFull.format(cal.time)
            updateDateTextDisplay()
            loadFriendDiaryData()
        } catch (e: Exception) {
            e.printStackTrace()
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
        try {
            val parsedDate = dateFormatFull.parse(selectedDate)
            if (parsedDate != null) {
                calendar.time = parsedDate
            }
        } catch (_: Exception) { }

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDate = dateFormatFull.format(calendar.time)
                updateDateTextDisplay()
                loadFriendDiaryData()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun loadFriendDiaryData() {
        val uid = targetUserId ?: return
        val safeContext = context ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1) 유저 정보 조회
                val userDoc = firestore.collection("users").document(uid).get().await()
                val nickname = userDoc.getString("nickname") ?: userDoc.getString("name") ?: "친구"
                val profileImg = userDoc.getString("profileImageUrl")

                // 2) 선택된 날짜의 포스트잇 가져오기 (Firestore)
                var postIts = emptyList<DiaryEntity>()
                try {
                    val querySnap = firestore.collection("diaries")
                        .whereEqualTo("userId", uid)
                        .whereEqualTo("createdAt", selectedDate)
                        .get()
                        .await()

                    postIts = querySnap.documents.mapNotNull { doc ->
                        try { doc.toObject(DiaryEntity::class.java) } catch (e: Exception) { null }
                    }
                } catch (e: Exception) {
                    Log.e("FriendDiaryDetail", "Firestore 조회 실패: ${e.message}")
                }

                // Room DB Fallback
                val db = AppDatabase.getDatabase(safeContext.applicationContext)
                if (postIts.isEmpty()) {
                    postIts = db.diaryDao().getPostItsByDateAndUserId(selectedDate, uid)
                }

                // 3) 댓글 정보 가져오기 (Local DB / Firestore)
                val comments = db.diaryDao().getCommentsByDateAndUserId(selectedDate, uid)

                withContext(Dispatchers.Main) {
                    val binding = _binding ?: return@withContext
                    if (!isAdded) return@withContext

                    binding.tvFriendNickname.text = nickname
                    binding.ivFriendProfile.load(profileImg) {
                        crossfade(true)
                        placeholder(R.drawable.ic_default_profile)
                        error(R.drawable.ic_default_profile)
                        transformations(CircleCropTransformation())
                    }

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
                                renderReadOnlyPostIt(postIt)
                            }
                        }
                    }

                    // 5) 댓글 그려주기
                    for (comment in comments) {
                        renderCommentPostIt(comment)
                    }
                }
            } catch (e: Exception) {
                Log.e("FriendDiaryDetail", "친구 일기 로드 실패: ${e.message}")
            }
        }
    }

    private fun renderReadOnlyPostIt(diary: DiaryEntity) {
        val safeContext = context ?: return
        val binding = _binding ?: return
        val inflater = LayoutInflater.from(safeContext)
        val view = inflater.inflate(R.layout.item_diary_postit, binding.layoutDetailDiaryContainer, false)

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

        // 마이페이지 다이어리와 100% 동일한 좌표 대입
        view.translationX = diary.positionX
        view.translationY = diary.positionY

        binding.layoutDetailDiaryContainer.addView(view)
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
        btnCommentDone.visibility = View.GONE

        makeViewDraggable(view)
        binding.layoutCommentsContainer.addView(view)
    }

    private fun addNewCommentPostIt() {
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

        val todayDateStr = dateFormatFull.format(Date())
        tvTime.text = todayDateStr
        commentView.tag = randomColor

        etCommentContent.isEnabled = true
        etCommentContent.requestFocus()

        btnCommentDone.setOnClickListener {
            val text = etCommentContent.text.toString().trim()
            if (text.isNotBlank()) {
                etCommentContent.isEnabled = false
                btnCommentDone.visibility = View.GONE
                makeViewDraggable(commentView)
                saveNewCommentToDb(commentView, text, randomColor, todayDateStr)
            } else {
                Toast.makeText(safeContext, "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.layoutCommentsContainer.addView(commentView)
        commentView.bringToFront()
    }

    private fun saveNewCommentToDb(view: View, text: String, color: String, timestamp: String) {
        val safeContext = context?.applicationContext ?: return
        val ownerUid = targetUserId ?: return

        val newComment = CommentEntity(
            diaryId = 0,
            userId = ownerUid,
            date = selectedDate,
            content = text,
            color = color,
            timestamp = timestamp,
            posX = view.translationX,
            posY = view.translationY
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(safeContext)
            val savedId = db.diaryDao().insertComment(newComment)

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    view.setTag(R.id.ivCommentBg, savedId.toInt())
                    Toast.makeText(safeContext, "댓글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                }
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
        (activity as? com.example.colorpaper.MainActivity)?.setBottomNavVisibility(false)
    }

    override fun onPause() {
        super.onPause()
        (activity as? com.example.colorpaper.MainActivity)?.setBottomNavVisibility(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(targetUserId: String, diaryId: Int = 0, targetDate: String? = null) =
            FriendDiaryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("targetUserId", targetUserId)
                    putInt("diaryId", diaryId)
                    putString("targetDate", targetDate)
                }
            }
    }
}