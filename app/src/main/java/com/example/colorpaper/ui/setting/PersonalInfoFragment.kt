package com.example.colorpaper.ui.setting

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentPersonalInfoBinding
import com.example.colorpaper.ui.login.LoginFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth

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
            AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠습니까?")
                .setPositiveButton("네") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    moveToLogin()
                }
                .setNegativeButton("아니오", null)
                .show()
        }

        // 4. 회원탈퇴
        binding.tvWithdraw.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("회원 탈퇴")
                .setMessage("탈퇴하면 모든 데이터가 삭제됩니다. 정말 탈퇴하시겠습니까?")
                .setPositiveButton("탈퇴하기") { _, _ ->
                    val user = FirebaseAuth.getInstance().currentUser
                    user?.delete()?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                            moveToLogin()
                        } else {
                            Toast.makeText(requireContext(), "탈퇴 실패: 다시 로그인 후 시도해주세요.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }

        // 5. 테마 색상 적용
        applyTheme(view)
    }

    private fun applyTheme(root: View) {
        val palette = ThemeManager.currentPalette(requireContext())
        val background = ContextCompat.getColor(requireContext(), palette.screenBackground)
        val text = ContextCompat.getColor(requireContext(), palette.primaryText)
        val surface = ContextCompat.getColor(requireContext(), palette.todo)
        val outline = ColorUtils.setAlphaComponent(text, 38)

        // 배경 및 텍스트/아이콘 틴트 적용
        root.setBackgroundColor(background)
        binding.ivBackPersonalInfo.imageTintList = ColorStateList.valueOf(text)

        tintTextRecursively(root, text)

        root.findViewById<MaterialCardView>(R.id.cv_personal_info_group)?.let { card ->
            card.setCardBackgroundColor(surface)
            card.strokeColor = outline
            card.strokeWidth = dp(1)
        }
    }

    private fun tintTextRecursively(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintTextRecursively(view.getChildAt(index), color)
            }
        }
    }

    private fun moveToLogin() {
        requireActivity().findViewById<View>(R.id.bottom_navigation_bar)?.visibility = View.GONE
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}