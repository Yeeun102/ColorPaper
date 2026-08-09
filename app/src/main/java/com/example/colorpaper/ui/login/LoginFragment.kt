package com.example.colorpaper.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.ui.home.HomeFragment
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val btnLogin = view.findViewById<Button>(R.id.btn_login)
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
                    (activity as? MainActivity)?.openDiaryDate("")
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