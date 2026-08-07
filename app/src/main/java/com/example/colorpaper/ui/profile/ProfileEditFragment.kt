package com.example.colorpaper.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.load
import coil.transform.CircleCropTransformation
import com.example.colorpaper.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

class ProfileEditFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private var selectedImageUri: Uri? = null

    // 갤러리에서 이미지 선택 포토 피커
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            // 선택된 이미지를 화면의 ImageView에 Coil로 표시
            view?.findViewById<ImageView>(R.id.ivEditProfileImage)?.load(uri) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }
    }

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

        // 🌟 1. 입력 칸을 누르면(Focus 진입 시) 내용 자동 지우기 설정
        etNickname.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) etNickname.setText("")
        }

        etUserCode.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) etUserCode.setText("")
        }

        // 🌟 2. 프로필 정보 로드 (기존 값 자동 세팅 & Coil로 프로필 이미지 로드)
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                if (etNickname.text.isEmpty()) etNickname.setText(user.nickname)
                if (etUserCode.text.isEmpty()) etUserCode.setText(user.userCode)

                // 🌟 이미지가 새로 선택되지 않은 상태에서만 기존 URL 또는 기본 이미지 로드
                if (selectedImageUri == null) {
                    ivEditProfileImage.load(user.profileImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_default_profile)
                        error(R.drawable.ic_default_profile)
                        transformations(CircleCropTransformation())
                    }
                }
            }
        }
        viewModel.fetchUserProfile()

        // 🌟 3. 저장 결과 관찰
        viewModel.saveResult.observe(viewLifecycleOwner) { isSuccess ->
            btnSave.isEnabled = true
            if (isSuccess) {
                Toast.makeText(requireContext(), "프로필이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "저장 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        ivBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // 🌟 4. 프로필 사진 클릭 시 갤러리 열기
        ivEditProfileImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // 🌟 5. 저장 버튼 클릭 시 (사진이 변경되었으면 Storage 업로드 후 Firestore 저장)
        btnSave.setOnClickListener {
            val inputNickname = etNickname.text.toString().trim()
            val inputUserCode = etUserCode.text.toString().trim()

            if (inputNickname.isEmpty() || inputUserCode.isEmpty()) {
                Toast.makeText(requireContext(), "빈칸을 모두 채워주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            if (selectedImageUri != null) {
                // 이미지가 새로 선택된 경우 Firebase Storage 업로드 진행
                uploadProfileImageAndSave(selectedImageUri!!, inputNickname, inputUserCode)
            } else {
                // 이미지 변경이 없으면 닉네임과 유저코드만 업데이트
                viewModel.saveUserProfile(inputNickname, inputUserCode, null)
            }
        }
    }

    private fun uploadProfileImageAndSave(imageUri: Uri, nickname: String, userCode: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: System.currentTimeMillis().toString()
        val storageRef = FirebaseStorage.getInstance().reference.child("profile_images/$uid.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // 이미지 업로드 성공 후 이미지 URL과 함께 프로필 정보 저장
                    viewModel.saveUserProfile(nickname, userCode, downloadUri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "이미지 업로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                view?.findViewById<Button>(R.id.btnSave)?.isEnabled = true
            }
    }
}