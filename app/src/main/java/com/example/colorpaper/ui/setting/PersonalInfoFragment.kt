package com.example.colorpaper.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.colorpaper.R
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

        // 1. 뒤로가기 버튼
        binding.ivBackPersonalInfo.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 2. 개인정보 변경 화면으로 이동
        binding.tvEditPersonalInfo.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PersonalInfoEditFragment())
                .addToBackStack(null)
                .commit()
        }

        // 3. 로그아웃
        binding.tvLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠습니까?")
                .setPositiveButton("네") { _, _ ->
                    // 파이어베이스 로그아웃 처리
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    moveToLogin() // 로그인 화면으로
                }
                .setNegativeButton("아니오", null) // 아니오 누르면 그냥 팝업 닫힘
                .show()
        }

        // 4. 회원탈퇴 (팝업창 띄우기)
        binding.tvWithdraw.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("회원 탈퇴")
                .setMessage("탈퇴하면 모든 데이터가 삭제됩니다. 정말 탈퇴하시겠습니까?")
                .setPositiveButton("탈퇴하기") { _, _ ->
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

                    // 파이어베이스에서 계정 완전히 삭제
                    user?.delete()?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                            moveToLogin() // 로그인 화면으로 쫓아내기
                        } else {
                            // 보안상 로그인한 지 너무 오래되면 탈퇴가 안 될 수 있음
                            Toast.makeText(requireContext(), "탈퇴 실패: 다시 로그인 후 시도해주세요.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    // 로그아웃이나 탈퇴 성공 시 '로그인 화면'으로 보내버리는 공통 함수
    private fun moveToLogin() {
        // 하단 네비게이션 바는 다시 숨겨주기!
        requireActivity().findViewById<View>(R.id.bottom_navigation_bar)?.visibility = View.GONE

        // 백스택 무시하고 바로 로그인 화면으로 덮어쓰기
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.example.colorpaper.ui.login.LoginFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}