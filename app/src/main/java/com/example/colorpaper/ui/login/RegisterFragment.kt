package com.example.colorpaper.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.colorpaper.R
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.colorpaper.ui.home.HomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [RegisterFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class RegisterFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName = view.findViewById<EditText>(R.id.et_name)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val etPasswordConfirm = view.findViewById<EditText>(R.id.et_password_confirm)
        val btnRegister = view.findViewById<Button>(R.id.btn_register)
        val tvToLogin = view.findViewById<TextView>(R.id.tv_to_login)

        btnRegister?.setOnClickListener {
            val name = etName?.text.toString().trim()
            val email = etEmail?.text.toString().trim()
            val password = etPassword?.text.toString().trim()
            val passwordConfirm = etPasswordConfirm?.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "모든 항목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != passwordConfirm) {
                Toast.makeText(requireContext(), "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performRegister(email, password) { isSuccess ->
                if (isSuccess) {
                    val uid = auth.currentUser?.uid ?: ""
                    // 🌟 4자리 임의의 유저 코드 생성 (예: 1234)
                    val generatedUserCode = (1000..9999).random().toString()

                    // 1. Firestore 저장 (필드명을 nickname, userCode로 통일)
                    val userMap = hashMapOf(
                        "uid" to uid,
                        "nickname" to name,
                        "email" to email,
                        "userCode" to generatedUserCode
                    )
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap)

                    // 2. 🌟 Room 로컬 DB에도 유저 정보 저장 (ProfileFragment 관찰용)
                    val db = AppDatabase.getDatabase(requireContext())
                    lifecycleScope.launch(Dispatchers.IO) {
                        val userEntity = UserEntity(
                            userCode = generatedUserCode,
                            email = email,
                            passwordHash = "", // 필요 시 저장
                            nickname = name,
                            profileImageUrl = null
                        )
                        db.userDao().insertUser(userEntity)
                    }

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()

                    activity?.findViewById<View>(R.id.bottom_navigation_bar)?.visibility = View.VISIBLE
                }
            }
        }

        tvToLogin?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
        }
    }

    // 회원가입 실행 함수
    fun performRegister(email: String, password: String, onResult: (Boolean) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "회원가입 성공", Toast.LENGTH_SHORT).show()
                    onResult(true)
                } else {
                    Toast.makeText(requireContext(), "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment RegisterFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            RegisterFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}