package com.example.colorpaper.ui.diary

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentDiaryDetailBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.CommentEntity
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

    // 💡 댓글용 전용 리소스 맵
    private val commentResourceMap = mapOf(
        "orange" to R.drawable.comment_orange,
        "yellow" to R.drawable.comment_yellow,
        "green"  to R.drawable.comment_green,
        "blue"   to R.drawable.comment_blue
    )

    private var targetDate: String = ""

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

        try {
            val sourceDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(targetDate)
            if (sourceDate != null) {
                binding.tvDetailDateTitle.text = SimpleDateFormat("M월 d일", Locale.getDefault()).format(sourceDate)
            } else {
                binding.tvDetailDateTitle.text = targetDate
            }
        } catch (e: Exception) {
            binding.tvDetailDateTitle.text = targetDate
        }

        // 과거 기록이므로 편집 기능(추가 버튼, 저장 버튼) 제거 및 숨김 규칙 적용
        binding.btnSaveDetail.visibility = View.VISIBLE
        binding.btnToolbarAddDetail.isEnabled = false
        binding.btnToolbarAddDetail.alpha = 0.3f

        // 기존 일기 및 저장된 댓글 로드
        loadDiaryAndComments()
        binding.btnSaveDetail.setOnClickListener {
            saveAllCommentsPositions()
        }

        // 💡 [정리 완료] 중복 메서드를 지우고 이벤트 리스너 내부를 정교하게 픽스했습니다.
        binding.btnToolbarCommentDetail.setOnClickListener {
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

            try {
                val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(targetDate)
                if (parsedDate != null) {
                    tvTime.text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(parsedDate)
                } else {
                    tvTime.text = targetDate
                }
            } catch (e: Exception) {
                tvTime.text = targetDate
            }
            commentView.tag = randomColor
            makeViewDraggable(commentView)
            // 4. 등록 버튼(TextView)을 누르면 입력된 값을 가져와서 Room DB에 최종 저장!
            btnCommentDone.setOnClickListener {
                val text = etCommentContent.text.toString().trim()
                if (text.isNotBlank()) {
                    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    tvTime.text = currentTime

                    // 완벽하게 락(Lock) 걸기: 수정 불가 상태로 전환 및 포커스 강제 해제
                    etCommentContent.isEnabled = false
                    etCommentContent.clearFocus()
                    btnCommentDone.visibility = View.GONE

                    insertCommentToDb(commentView, text, randomColor, currentTime)

                } else {
                    Toast.makeText(requireContext(), "댓글 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            // 5. 생성된 따끈따끈한 댓글 포스트잇을 화면 컨테이너에 즉시 추가
            binding.layoutCommentsContainer.addView(commentView)
        }
    }

    private fun loadDiaryAndComments() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            val postIts = withContext(Dispatchers.IO) { db.diaryDao().getPostItsByDate(targetDate) }
            val comments = withContext(Dispatchers.IO) { db.diaryDao().getCommentsByDate(targetDate) }

            // 일기 데이터 그리기 (편집 불가능 구조)
            binding.layoutDetailDiaryContainer.removeAllViews()
            for (postIt in postIts) {
                renderReadOnlyPostIt(postIt)
            }

            // 셀프 댓글 데이터 그리기 (계단식 뷰 스택)
            binding.layoutCommentsContainer.removeAllViews()
            for (comment in comments) {
                renderCommentPostIt(comment)
            }
        }
    }

    private fun insertCommentToDb(view: View, commentText: String, colorName: String, timestamp: String) {
        val posX = view.translationX
        val posY = view.translationY

        val newComment = CommentEntity(
            diaryId = 0,
            userId = 1,
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
                // 뷰에 저장된 commentId 세팅 (이후 위치 업데이트를 위해 저장)
                view.setTag(R.id.ivCommentBg, savedId.toInt())
                Toast.makeText(requireContext(), "셀프 댓글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAllCommentsPositions() {
        val container = binding.layoutCommentsContainer
        val childCount = container.childCount

        if (childCount == 0) {
            Toast.makeText(requireContext(), "저장할 댓글이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())

            withContext(Dispatchers.Main) {
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
                        val timeStr = tvTime.text.toString()

                        val updatedComment = CommentEntity(
                            commentId = existingCommentId,
                            diaryId = 0,
                            userId = 1,
                            date = targetDate,
                            content = text,
                            color = colorName,
                            timestamp = timeStr,
                            posX = posX,
                            posY = posY
                        )

                        lifecycleScope.launch(Dispatchers.IO) {
                            val savedId = db.diaryDao().insertComment(updatedComment)
                            withContext(Dispatchers.Main) {
                                commentView.setTag(R.id.ivCommentBg, savedId.toInt())
                            }
                        }
                    }
                }
                Toast.makeText(requireContext(), "댓글 위치가 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderReadOnlyPostIt(diary: DiaryEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_diary_postit, binding.layoutDetailDiaryContainer, false)

        val ivBg = view.findViewById<ImageView>(R.id.ivPostItBg)
        val etContent = view.findViewById<TextView>(R.id.etPostItContent)

        etContent.text = diary.content
        etContent.isEnabled = false

        // 일기 원본 색상 SVG 매핑
        val resId = postItResourceMap[diary.color] ?: R.drawable.post_yellow
        ivBg.setImageResource(resId)

        view.translationX = diary.positionX
        view.translationY = diary.positionY

        binding.layoutDetailDiaryContainer.addView(view)
    }

    // 과거의 일기를 켜서 '이미 저장되어 있던 댓글'들을 불러와서 그릴 때 (완벽한 잠금 모드)
    private fun renderCommentPostIt(comment: CommentEntity) {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_diary_comment, binding.layoutCommentsContainer, false)

        val ivCommentBg = view.findViewById<ImageView>(R.id.ivCommentBg)
        val etCommentContent = view.findViewById<EditText>(R.id.etCommentContent)
        val tvTime = view.findViewById<TextView>(R.id.tvCommentTime)
        val btnCommentDone = view.findViewById<TextView>(R.id.btnCommentDone)

        // 기존 데이터 셋업 (CommentEntity 프로퍼티 바인딩)
        etCommentContent.setText(comment.content)
        tvTime.text = comment.timestamp

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}