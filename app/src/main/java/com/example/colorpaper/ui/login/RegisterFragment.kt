package com.example.colorpaper.ui.login

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.colorpaper.R
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.UserEntity
import com.example.colorpaper.databinding.FragmentRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.colorpaper.ui.home.HomeFragment
import com.example.colorpaper.ui.theme.ThemeManager
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

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. ThemeManager에서 현재 테마 팔레트 가져오기
        val palette = ThemeManager.currentPalette(requireContext())

        // 2. 배경색 바꾸기 (root 뷰를 가져와서 배경 설정)
        val rootLayout = view.findViewById<View>(R.id.root_layout) // 👈 XML 최상위 레이아웃 ID
        rootLayout.setBackgroundResource(palette.screenBackground)


        // 4. 로고 이미지 바꾸기
        //val ivLogo = view.findViewById<ImageView>(R.id.iv_app_logo)
        //ivLogo.setImageResource(palette.toolbarLogo)

        // 1. XML에 있는 ID랑 정확하게 똑같이 맞춰서 뷰(화면 요소)들 가져오기!
        val etName = view.findViewById<EditText>(R.id.et_name)
        val etContact = view.findViewById<EditText>(R.id.et_contact) // 👈 et_email에서 et_contact로 수정됨!
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val etPasswordConfirm = view.findViewById<EditText>(R.id.et_password_confirm)
        val btnRegister = view.findViewById<Button>(R.id.btn_register)
        val tvToLogin = view.findViewById<TextView>(R.id.tv_to_login)
        val btnVerify = view.findViewById<Button>(R.id.btn_verify) // 👈 XML에 있던 인증 버튼 추가!

        // 3. 버튼 색상 바꾸기
        //val btnLogin = view.findViewById<Button>(R.id.btn_login)
        val strokeColor = ContextCompat.getColor(requireContext(), palette.stroke)
        val buttonColor = ContextCompat.getColor(requireContext(), palette.reminder)
        binding.btnRegister.backgroundTintList = ColorStateList.valueOf(strokeColor) // 👈 테마별 포인트 컬러(accent) 적용
        binding.btnVerify.backgroundTintList = ColorStateList.valueOf(buttonColor)

        // 인증 버튼 눌렀을 때 작동할 코드
        btnVerify?.setOnClickListener {
            val contact = etContact?.text.toString().trim()
            if (contact.isEmpty()) {
                Toast.makeText(requireContext(), "이메일(연락처)을 먼저 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 임시 인증 안내 메시지 (파이어베이스는 보통 가입 완료 후에 인증 메일을 보내!)
            Toast.makeText(requireContext(), "사용 가능한 이메일입니다!", Toast.LENGTH_SHORT).show()
        }

        // 가입 완료 버튼 눌렀을 때 작동할 코드
        btnRegister?.setOnClickListener {
            val name = etName?.text.toString().trim()
            val email = etContact?.text.toString().trim() // 👈 가져온 입력값을 email 변수에 담음
            val password = etPassword?.text.toString().trim()
            val passwordConfirm = etPasswordConfirm?.text.toString().trim()

            // 빈칸 검사
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "모든 항목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 비밀번호 일치 검사
            if (password != passwordConfirm) {
                Toast.makeText(requireContext(), "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 이메일 형식(test@test.com)이 맞는지 검사하는 강력한 방패!
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "올바른 이메일 형식을 적어주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 모든 검사 통과! 파이어베이스로 진짜 가입 요청 날리기
            performRegister(email, password) { isSuccess ->
                if (isSuccess) {
                    val uid = auth.currentUser?.uid ?: ""
                    val generatedUserCode = (1000..9999).random().toString()

                    // 1. 파이어베이스 Firestore 저장
                    val userMap = hashMapOf(
                        "uid" to uid,
                        "nickname" to name,
                        "email" to email,
                        "userCode" to generatedUserCode
                    )
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap)

                    // 2. 룸(Room) 로컬 DB 저장
                    val db = AppDatabase.getDatabase(requireContext())
                    lifecycleScope.launch(Dispatchers.IO) {
                        val userEntity = UserEntity(
                            userCode = generatedUserCode,
                            email = email,
                            passwordHash = "",
                            nickname = name,
                            profileImageUrl = null
                        )
                        db.userDao().insertUser(userEntity)
                    }

                    // 3. 성공했으니 홈 화면으로 스르륵 이동!
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()

                    activity?.findViewById<View>(R.id.bottom_navigation_bar)?.visibility = View.VISIBLE
                }
            }
        }

        // 로그인 화면으로 돌아가기 버튼
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