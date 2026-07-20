package com.example.colorpaper.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.UserEntity
import kotlinx.coroutines.launch

class ProfileEditFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile_edit, container, false)

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val ivEditProfileImage = view.findViewById<ImageView>(R.id.ivEditProfileImage)
        val etNickname = view.findViewById<EditText>(R.id.etNickname)
        val etUserCode = view.findViewById<EditText>(R.id.etUserCode)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val db = AppDatabase.getDatabase(requireContext())

        // 1. 기존에 저장된 내 정보(user_id = 1)가 있다면 입력창에 미리 세팅해주기
        viewLifecycleOwner.lifecycleScope.launch {
            val myInfo = db.userDao().getUserById(1)
            if (myInfo != null) {
                etNickname.setText(myInfo.nickname)
                etUserCode.setText(myInfo.userCode) // 새로 도입한 userCode를 입력칸에 바인딩
            }
        }

        // 2. 뒤로가기 버튼 클릭 시 화면 탈출
        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 3. 프로필 이미지 변경 클릭 시 알림
        ivEditProfileImage.setOnClickListener {
            Toast.makeText(context, "갤러리 열기 기능 준비 중!", Toast.LENGTH_SHORT).show()
        }

        // 4. 저장 버튼 클릭 시 -> 입력값 무결성 체크 후 Room DB 업데이트
        btnSave.setOnClickListener {
            val inputNickname = etNickname.text.toString().trim()
            val inputUserCode = etUserCode.text.toString().trim()

            if (inputNickname.isEmpty() || inputUserCode.isEmpty()) {
                Toast.makeText(context, "빈칸을 모두 채워주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                // 기존 데이터 가져와서 누락되는 컬럼 방지
                val existingUser = db.userDao().getUserById(1)

                val updatedUser = UserEntity(
                    userId = 1, // 기존 1번 유저 정보를 교체
                    userCode = inputUserCode, // 새로 입력받은 사용자 ID 세팅
                    email = existingUser?.email ?: "default@email.com",
                    passwordHash = existingUser?.passwordHash ?: "default_hash",
                    nickname = inputNickname, // 새로 입력받은 닉네임 세팅
                    profileImageUrl = existingUser?.profileImageUrl,
                    membershipStatus = existingUser?.membershipStatus ?: "FREE",
                    pushEnabled = existingUser?.pushEnabled ?: true,
                    createdAt = existingUser?.createdAt ?: System.currentTimeMillis()
                )

                // DB에 반영
                db.userDao().insertUser(updatedUser)

                Toast.makeText(context, "프로필이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()

                // 저장이 완료되었으므로 이전 프로필 홈 화면으로 자동 복귀
                parentFragmentManager.popBackStack()
            }
        }

        return view
    }
}