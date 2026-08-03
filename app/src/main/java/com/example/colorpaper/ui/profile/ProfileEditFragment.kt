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
        return inflater.inflate(R.layout.fragment_profile_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val ivEditProfileImage = view.findViewById<ImageView>(R.id.ivEditProfileImage)
        val etNickname = view.findViewById<EditText>(R.id.etNickname)
        val etUserCode = view.findViewById<EditText>(R.id.etUserCode)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val db = AppDatabase.getDatabase(requireContext())

        // 1. 기존 정보 초기화 (내 정보 user_id = 1)
        viewLifecycleOwner.lifecycleScope.launch {
            val myInfo = db.userDao().getUserById(1)
            if (myInfo != null) {
                etNickname.setText(myInfo.nickname)
                etUserCode.setText(myInfo.userCode)
            }
        }

        // 2. 뒤로가기
        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 3. 프로필 이미지 변경 클릭
        ivEditProfileImage.setOnClickListener {
            Toast.makeText(requireContext(), "갤러리 열기 기능 준비 중!", Toast.LENGTH_SHORT).show()
        }

        // 4. 저장 버튼 클릭
        btnSave.setOnClickListener {
            val inputNickname = etNickname.text.toString().trim()
            val inputUserCode = etUserCode.text.toString().trim()

            if (inputNickname.isEmpty() || inputUserCode.isEmpty()) {
                Toast.makeText(requireContext(), "빈칸을 모두 채워주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 중복 클릭 방지
            btnSave.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val existingUser = db.userDao().getUserById(1)

                val updatedUser = UserEntity(
                    userId = 1,
                    userCode = inputUserCode,
                    email = existingUser?.email ?: "default@email.com",
                    passwordHash = existingUser?.passwordHash ?: "default_hash",
                    nickname = inputNickname,
                    profileImageUrl = existingUser?.profileImageUrl,
                    membershipStatus = existingUser?.membershipStatus ?: "FREE",
                    pushEnabled = existingUser?.pushEnabled ?: true,
                    createdAt = existingUser?.createdAt ?: System.currentTimeMillis()
                )

                db.userDao().insertUser(updatedUser)

                Toast.makeText(requireContext(), "프로필이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }
}