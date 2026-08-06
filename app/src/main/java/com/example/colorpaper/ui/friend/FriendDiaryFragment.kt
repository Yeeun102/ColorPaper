package com.example.colorpaper.ui.friend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryCommentEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FriendDiaryFragment : Fragment() {

    private var targetUserId: Int = 2 // 기본 테스트 유저 ID
    private val myUserId: Int = 1     // 내 ID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_friend_diary, container, false)
    }
    // 댓글 등록 버튼 눌렀을 때
    private fun saveComment(emoji: String, content: String) {
        val currentDate = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())

        // 1. Entity 객체 생성 (데이터 깔끔하게 분리!)
        val commentEntity = DiaryCommentEntity(
            diaryId = 101L,              // 해당 다이어리 ID
            writerId = myUserId,          // 작성자 (나)
            writerName = "나으닝",        // 작성자 닉네임
            ownerId = targetUserId,      // 누구 다이어리인지 (친구)
            emoji = emoji,               // 이모지
            content = content,           // 내용
            createdAt = currentDate      // 언제
        )

        lifecycleScope.launch {
            // 2. Room 로컬 DB에 저장
            val db = AppDatabase.getDatabase(requireContext())
            db.diaryCommentDao().insertComment(commentEntity)

            // 3. Firebase Cloud에도 동일하게 저장 (팀원 공유용)
            FirebaseFirestore.getInstance()
                .collection("diary_comments")
                .add(commentEntity)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "댓글 등록 성공! 🎉", Toast.LENGTH_SHORT).show()
                }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val tvFriendName = view.findViewById<TextView>(R.id.tvFriendName)
        val btnGoToProfile = view.findViewById<Button>(R.id.btnGoToProfile)
        val llPublicContent = view.findViewById<LinearLayout>(R.id.llPublicContent)
        val llBlurOverlay = view.findViewById<LinearLayout>(R.id.llBlurOverlay)
        val tvEmojiReaction = view.findViewById<TextView>(R.id.tvEmojiReaction)
        val tvDiaryContent = view.findViewById<TextView>(R.id.tvDiaryContent)

        val db = AppDatabase.getDatabase(requireContext())

        // 1. 뒤로가기
        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. 7.1.2.3 / 7.1.2 프로필 홈 버튼 클릭 시 ➔ 친구 프로필 홈으로 이동
        btnGoToProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FriendProfileFragment.newInstance(targetUserId))
                .addToBackStack(null)
                .commit()
        }

        // 3. 팔로우 상태 체크 및 다이어리 불러오기
        viewLifecycleOwner.lifecycleScope.launch {
            // 내가 이 친구를 팔로우했는지 확인
            val friends = db.userDao().getMyFriends(myUserId)
            val isFollowing = friends.any { it.userId == targetUserId }

            if (isFollowing) {
                // 팔로우 후: 내용 공개
                llPublicContent.visibility = View.VISIBLE
                llBlurOverlay.visibility = View.GONE

                val friendUser = db.userDao().getUserById(targetUserId)
                if (friendUser != null) {
                    tvFriendName.text = "${friendUser.nickname}의 다이어리"
                    tvDiaryContent.text = "${friendUser.nickname}님의 오늘 기록:\n\n오늘도 열심히 코딩을 했다! 앱 생태계 완성까지 얼마 안 남았다. 화이팅!"
                }
            } else {
                // 팔로우 전: 블러(안내) 화면 표시
                llPublicContent.visibility = View.VISIBLE
                llBlurOverlay.visibility = View.VISIBLE
            }
        }

        // 4. [7.1.1] 이모지 반응 클릭 시 ➔ 댓글 / 반응 남기기 팝업 (토스트 대체)
        tvEmojiReaction.setOnClickListener {
            Toast.makeText(requireContext(), "친구 다이어리에 '좋아요' 및 댓글 반응을 남겼습니다! ❤️", Toast.LENGTH_SHORT).show()
        }
    }
}