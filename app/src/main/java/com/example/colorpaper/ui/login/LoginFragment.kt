package com.example.colorpaper.ui.login

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentLoginBinding
import com.example.colorpaper.ui.home.HomeFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. ThemeManager에서 현재 테마 팔레트 가져오기
        val palette = ThemeManager.currentPalette(requireContext())

        // 2. 배경색 바꾸기 (root 뷰를 가져와서 배경 설정)
        val rootLayout = view.findViewById<View>(R.id.root_layout) // 👈 XML 최상위 레이아웃 ID
        rootLayout.setBackgroundResource(palette.screenBackground)

        // 3. 버튼 색상 바꾸기
        val btnLogin = view.findViewById<Button>(R.id.btn_login)
        //btnLogin.setBackgroundResource(palette.accent) // 👈 테마별 포인트 컬러(accent) 적용
        val strokeColor = ContextCompat.getColor(requireContext(), palette.stroke)
        binding.btnLogin.backgroundTintList = ColorStateList.valueOf(strokeColor) // 👈 테마별 포인트 컬러(accent) 적용

        // 4. 로고 이미지 바꾸기
        val ivLogo = view.findViewById<ImageView>(R.id.iv_app_logo)
        ivLogo.setImageResource(palette.toolbarLogo)

        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val tvToRegister = view.findViewById<TextView>(R.id.tv_to_register)

        btnLogin?.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            val email = etEmail?.text.toString().trim()
            val password = etPassword?.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(safeContext, "이메일과 비밀번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(email, password) { isSuccess ->
                // UI 전환 전 프래그먼트 상태 검증
                if (!isAdded || isStateSaved) return@performLogin

                if (isSuccess) {
                    (activity as? MainActivity)?.openHome()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commitAllowingStateLoss()

                    activity?.findViewById<View>(R.id.bottom_navigation_bar)?.visibility = View.VISIBLE
                }
            }
        }

        tvToRegister?.setOnClickListener {
            if (!isAdded || isStateSaved) return@setOnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RegisterFragment())
                .commit()
        }
    }

    // 로그인 실행 함수
    fun performLogin(email: String, password: String, onResult: (Boolean) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task -> // requireActivity() 인자 제거하여 안전화
                // 통신이 끝난 시점에 Fragment가 안전하게 붙어있는지 확인
                val safeContext = context ?: return@addOnCompleteListener
                if (!isAdded) return@addOnCompleteListener

                if (task.isSuccessful) {
                    Toast.makeText(safeContext, "로그인 성공", Toast.LENGTH_SHORT).show()
                    onResult(true)
                } else {
                    Toast.makeText(safeContext, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            }
    }
}