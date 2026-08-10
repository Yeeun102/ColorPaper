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
import androidx.fragment.app.viewModels
import com.example.colorpaper.R

class ProfileEditFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private var isInitialLoaded = false

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

        // 1. 프로필 정보 관찰 (최초 1회만 입력창 초기화)
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                if (!isInitialLoaded) {
                    etNickname.setText(user.nickname)
                    etUserCode.setText(user.userCode)
                    isInitialLoaded = true
                }

                ivEditProfileImage.setImageResource(R.drawable.ic_default_profile)
            }
        }
        viewModel.fetchUserProfile()

        // 2. 저장 성공 여부 관찰 (안전한 Context 및 Fragment 상태 검증 추가)
        viewModel.saveResult.observe(viewLifecycleOwner) { isSuccess ->
            val safeContext = context ?: return@observe
            if (!isAdded) return@observe

            btnSave.isEnabled = true
            if (isSuccess) {
                Toast.makeText(safeContext, "프로필이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
                if (!isStateSaved) {
                    parentFragmentManager.popBackStack()
                }
            } else {
                Toast.makeText(safeContext, "저장 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 뒤로가기 버튼
        ivBack.setOnClickListener {
            if (isAdded && !isStateSaved) {
                parentFragmentManager.popBackStack()
            }
        }

        // 4. 프로필 이미지 선택
        ivEditProfileImage.setOnClickListener {
            Toast.makeText(requireContext(), "프로필 이미지 기능은 현재 사용하지 않습니다.", Toast.LENGTH_SHORT).show()
        }

        // 5. 저장 버튼
        btnSave.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            val inputNickname = etNickname.text.toString().trim()
            val inputUserCode = etUserCode.text.toString().trim()

            if (inputNickname.isEmpty() || inputUserCode.isEmpty()) {
                Toast.makeText(safeContext, "빈칸을 모두 채워주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            viewModel.saveUserProfile(inputNickname, inputUserCode, null)
        }
    }
}