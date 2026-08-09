package com.example.colorpaper.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.colorpaper.databinding.FragmentPersonalInfoBinding

class PersonalInfoFragment : Fragment() {

    private var _binding: FragmentPersonalInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기 버튼
        binding.ivBackPersonalInfo.setOnClickListener {
            // TODO: 이전 설정 화면으로 돌아가는 코드 추가
            Toast.makeText(requireContext(), "뒤로 가기 (임시)", Toast.LENGTH_SHORT).show()
        }

        // 8.1.1 개인정보변경
        binding.tvEditPersonalInfo.setOnClickListener {
            // TODO: 개인정보 변경 화면으로 이동
            Toast.makeText(requireContext(), "개인정보 변경 화면으로 이동 (임시)", Toast.LENGTH_SHORT).show()
        }

        // 8.1.2 로그아웃
        binding.tvLogout.setOnClickListener {
            // TODO: 로그아웃 다이얼로그 띄우기 or 처리
            Toast.makeText(requireContext(), "로그아웃 누름 (임시)", Toast.LENGTH_SHORT).show()
        }

        // 8.1.3 회원탈퇴
        binding.tvWithdraw.setOnClickListener {
            // TODO: 회원탈퇴 다이얼로그 띄우기 or 처리
            Toast.makeText(requireContext(), "회원탈퇴 누름 (임시)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}