package com.example.colorpaper.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.colorpaper.databinding.FragmentPersonalInfoEditBinding

class PersonalInfoEditFragment : Fragment() {

    private var _binding: FragmentPersonalInfoEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalInfoEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 뒤로가기 버튼
        binding.ivBackEditInfo.setOnClickListener {
            // TODO: 개인정보(8.1) 화면으로 돌아가는 코드 추가
            Toast.makeText(requireContext(), "뒤로 가기 (임시)", Toast.LENGTH_SHORT).show()
        }

        // 2. 이메일/전화번호 라디오 버튼 선택에 따라 입력창 힌트 바꾸기 (디테일 챙기기!)
        binding.rgVerifyMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbEmail.id -> {
                    binding.etVerifyInfo.hint = "이메일 입력"
                }
                binding.rbPhone.id -> {
                    binding.etVerifyInfo.hint = "전화번호 입력 (- 제외)"
                }
            }
        }

        // 3. 인증 버튼 클릭
        binding.btnVerify.setOnClickListener {
            val verifyInfo = binding.etVerifyInfo.text.toString()
            if (verifyInfo.isNotBlank()) {
                Toast.makeText(requireContext(), "인증번호가 발송되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "정보를 먼저 입력해주세요!", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. 저장하기 버튼 클릭
        binding.btnSavePersonalInfo.setOnClickListener {
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etNewPasswordConfirm.text.toString()

            if (newPassword.isNotBlank() && newPassword == confirmPassword) {
                Toast.makeText(requireContext(), "정보가 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                // TODO: 실제 백엔드에 정보 수정 요청 보내는 로직 추가
            } else if (newPassword != confirmPassword) {
                Toast.makeText(requireContext(), "비밀번호가 서로 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "저장할 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}