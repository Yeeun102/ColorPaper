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
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.colorpaper.data.local.UserDao

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 1. 피그마 레이아웃 껍데기 연결
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // 2. 피그마 디자인에 배치된 모든 뷰 컴포넌트 ID 연결
        val tvUserCode = view.findViewById<TextView>(R.id.tvUserCode)         // 상단 유저코드 (예: #j32ifiewjo)
        val tvNickname = view.findViewById<TextView>(R.id.tvNickname)         // 닉네임 (예: 나으닝)
        val ivEditProfile = view.findViewById<ImageView>(R.id.ivEditProfile)   // 6.1 프로필 수정 (연필 아이콘)
        val btnFriendUpdate = view.findViewById<LinearLayout>(R.id.layoutFriendUpdate) // 6.5 친구 업데이트 뱃지/버튼
        val layoutHighlights = view.findViewById<LinearLayout>(R.id.layoutHighlights) // 6.2 내 하이라이트 구역
        val layoutSharedVocab = view.findViewById<LinearLayout>(R.id.layoutSharedVocab) // 6.3 내 공유 단어장 구역
        val ivMyDiary = view.findViewById<ImageView>(R.id.ivMyDiary)           // 6.4 내 공유 다이어리 (큰 다이어리 이미지)
        val ivSearchFriend = view.findViewById<ImageView>(R.id.ivSearchFriend) // 6.6 친구 검색 (우측 상단 돋보기)

        // 3. DB에서 내 정보(user_id = 1) 가져와서 상단에 실시간 반영하기 (코루틴 적용)
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val myInfo = db.userDao().getUserById(1)

            if (myInfo != null) {
                tvUserCode.text = "#${myInfo.userCode}"
                tvNickname.text = myInfo.nickname
            } else {
                // DB에 데이터가 없으면 피그마 기본값 띄워주기
                tvUserCode.text = "#j32ifiewjo"
                tvNickname.text = "나으닝"
            }
        }

        // 4. [6.1 프로필 수정] 연필 아이콘 클릭 시
        ivEditProfile.setOnClickListener {
            Toast.makeText(context, "6.1 프로필 수정 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
            // TODO: parentFragmentManager 이용해 ProfileEditFragment로 교체하거나 Activity 띄우기
        }

        // 5. [6.2 내 하이라이트 보기] 동그라미 구역 클릭 시
        layoutHighlights.setOnClickListener {
            Toast.makeText(context, "6.2 내 하이라이트 보기", Toast.LENGTH_SHORT).show()
        }

        // 6. [6.3 내 공유 단어장 보기] 네모 상자 클릭 시
        layoutSharedVocab.setOnClickListener {
            Toast.makeText(context, "6.3 내 공유 단어장 보기", Toast.LENGTH_SHORT).show()
        }

        // 7. [6.4 내 공유 다이어리 보기] 하단 큰 다이어리 클릭 시
        ivMyDiary.setOnClickListener {
            Toast.makeText(context, "6.4 내 공유 다이어리 보기", Toast.LENGTH_SHORT).show()
        }

        // 8. [6.5 업데이트한 친구 다이어리 보기] 뱃지 버튼 클릭 시
        btnFriendUpdate.setOnClickListener {
            Toast.makeText(context, "6.5 업데이트한 친구 다이어리 목록으로 이동", Toast.LENGTH_SHORT).show()
            // TODO: 7.1 친구 프로필 다이어리 화면으로 이동하는 로직
        }

        // 9. [6.6 친구 검색하기] 우측 상단 돋보기 클릭 시 -> [7. 친구로 이동]
        ivSearchFriend.setOnClickListener {
            Toast.makeText(context, "6.6 친구 검색 화면으로 이동 (7번 기능)", Toast.LENGTH_SHORT).show()
            // TODO: ui/friend/ 폴더에 있는 친구 검색용 Fragment로 전환하기
        }

        return view
    }
}