package com.example.colorpaper.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.ui.friend.FriendListFragment // 7. 친구 검색/목록 프래그먼트
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // XML 뷰 바인딩
        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val tvUserCodeTop = view.findViewById<TextView>(R.id.tvUserCodeTop)
        val ivSearchFriend = view.findViewById<ImageView>(R.id.ivSearchFriend)
        val ivEditProfile = view.findViewById<ImageView>(R.id.ivEditProfile)
        val tvNickname = view.findViewById<TextView>(R.id.tvNickname)
        val tvFriendUpdateBadge = view.findViewById<TextView>(R.id.tvFriendUpdateBadge)
        val llSharedFlashcard = view.findViewById<LinearLayout>(R.id.llSharedFlashcard)
        val llSharedDiary = view.findViewById<LinearLayout>(R.id.llSharedDiary)

        val db = AppDatabase.getDatabase(requireContext())

        // 1. 내 정보(user_id=1) DB에서 가져와 화면 상단에 실시간 동기화
        viewLifecycleOwner.lifecycleScope.launch {
            val myInfo = db.userDao().getUserById(1)
            if (myInfo != null) {
                tvNickname.text = myInfo.nickname
                tvUserCodeTop.text = "#${myInfo.userCode}"
            }
        }

        // 2. 뒤로가기 화살표 클릭
        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 3. 6.1 우측 상단 연필 아이콘 클릭 시 -> 프로필 수정하기로 이동
        ivEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileEditFragment())
                .addToBackStack(null)
                .commit()
        }

        // 4. 6.6 우측 상단 돋보기 클릭 시 -> [7. 친구 검색/리스트] 화면으로 점프
        ivSearchFriend.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FriendListFragment())
                .addToBackStack(null)
                .commit()
        }

        // 5. 6.5 친구 업데이트 배지 클릭 시 -> 업데이트 한 친구 다이어리 피드로 이동
        tvFriendUpdateBadge.setOnClickListener {
            Toast.makeText(context, "새로 올라온 친구들의 다이어리를 띄웁니다!", Toast.LENGTH_SHORT).show()
            // 추후 친구 피드 전용 화면 구현 시 여기에 Fragment 전환 코드 연결
        }

        // 6.3 공유중인 단어장 구역 클릭 시 -> 진짜 DB에서 내가 공유한 세트 긁어오기
        llSharedFlashcard.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val mySharedSets = db.flashcardDao().getFlashcardSetsByVisibility(1, "전체공개")

                if (mySharedSets.isEmpty()) {
                    Toast.makeText(context, "공유 중인 단어장이 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val titles = mySharedSets.joinToString { it.title }
                    Toast.makeText(context, "공유 단어장: $titles", Toast.LENGTH_LONG).show()
                }
            }
        }

// 6.4 중앙의 다이어리 책 커버 클릭 시 -> 진짜 DB에서 내 공개 다이어리 긁어오기
        llSharedDiary.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // 기획서 6.4: 내 공유 다이어리 목록 가져오기
                val myPublicDiaries = db.diaryDao().getDiariesByVisibility(1, "PUBLIC")

                if (myPublicDiaries.isEmpty()) {
                    Toast.makeText(context, "공개 중인 다이어리가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val contents = myPublicDiaries.joinToString { it.content.take(10) + "..." }
                    Toast.makeText(context, "공개 다이어리: $contents", Toast.LENGTH_LONG).show()
                }
            }
        }

        return view
    }
}