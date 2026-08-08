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

class ProfileEditFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    private var isInitialLoaded = false

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
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

        // 1. 프로필 정보 관찰 (최초 1회만 입력창 초기화)
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                if (!isInitialLoaded) {
                    etNickname.setText(user.nickname)
                    etUserCode.setText(user.userCode)
                    isInitialLoaded = true
                }

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

        // 2. 저장 성공 여부 관찰
        viewModel.saveResult.observe(viewLifecycleOwner) { isSuccess ->
            btnSave.isEnabled = true
            if (isSuccess) {
                Toast.makeText(requireContext(), "프로필이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "저장 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 버튼 클릭 리스너 연동
        ivBack.setOnClickListener { parentFragmentManager.popBackStack() }

        ivEditProfileImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSave.setOnClickListener {
            val inputNickname = etNickname.text.toString().trim()
            val inputUserCode = etUserCode.text.toString().trim()

            if (inputNickname.isEmpty() || inputUserCode.isEmpty()) {
                Toast.makeText(requireContext(), "빈칸을 모두 채워주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            val imageUriString = selectedImageUri?.toString()
            viewModel.saveUserProfile(inputNickname, inputUserCode, imageUriString)
        }
    }
}