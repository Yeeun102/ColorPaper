//package com.example.colorpaper.ui.friend
//
//import android.content.ClipData
//import android.content.ClipboardManager
//import android.content.Context
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.core.view.isVisible
//import androidx.fragment.app.Fragment
//import androidx.lifecycle.lifecycleScope
//import coil.load
//import coil.transform.CircleCropTransformation
//import com.example.colorpaper.R
//import com.example.colorpaper.databinding.FragmentFriendProfileBinding
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
//import kotlinx.coroutines.withContext
//
//class FriendProfileFragment : Fragment() {
//
//    private var _binding: FragmentFriendProfileBinding? = null
//    private val binding get() = _binding!!
//
//    private val auth = FirebaseAuth.getInstance()
//    private val firestore = FirebaseFirestore.getInstance()
//    private var targetUserId: String? = null
//
//    companion object {
//        private const val ARG_TARGET_USER_ID = "TARGET_USER_ID"
//
//        fun newInstance(targetUserId: String? = null): FriendProfileFragment {
//            val fragment = FriendProfileFragment()
//            fragment.arguments = Bundle().apply {
//                putString(ARG_TARGET_USER_ID, targetUserId)
//            }
//            return fragment
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        targetUserId = arguments?.getString(ARG_TARGET_USER_ID) ?: auth.currentUser?.uid
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentFriendProfileBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        val myUid = auth.currentUser?.uid
//        val isMyProfile = (targetUserId == myUid)
//
//        // 내 프로필이면 팔로우 버튼 숨김 & 코드복사 버튼 보이기 (반대의 경우 반대로)
//        binding.btnFollowToggle.isVisible = !isMyProfile
//        binding.btnCopyCode.isVisible = isMyProfile
//
//        // 1. 뒤로가기 (ivBack)
//        binding.ivBack.setOnClickListener {
//            parentFragmentManager.popBackStack()
//        }
//
//        // 2. 상단 돋보기 클릭 -> 친구 검색 화면 이동 (ivSearchFriend)
//        binding.ivSearchFriend.setOnClickListener {
//            parentFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, FriendListFragment.newInstance(null, "SEARCH"))
//                .addToBackStack(null)
//                .commit()
//        }
//
//        // 3. 팔로워 클릭 -> 팔로워 목록 이동 (tvFollowerCount)
//        binding.tvFollowerCount.setOnClickListener {
//            parentFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, FriendListFragment.newInstance(targetUserId, "FOLLOWERS"))
//                .addToBackStack(null)
//                .commit()
//        }
//
//        // 4. 팔로잉 클릭 -> 팔로잉 목록 이동 (tvFollowingCount)
//        binding.tvFollowingCount.setOnClickListener {
//            parentFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, FriendListFragment.newInstance(targetUserId, "FOLLOWING"))
//                .addToBackStack(null)
//                .commit()
//        }
//
//        // 5. 유저 코드 복사 버튼 (btnCopyCode)
//        binding.btnCopyCode.setOnClickListener {
//            val userCode = binding.tvUserCodeTop.text.toString().removePrefix("#")
//            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//            val clip = ClipData.newPlainText("UserCode", userCode)
//            clipboard.setPrimaryClip(clip)
//            Toast.makeText(requireContext(), "유저 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show()
//        }
//
//        // 6. 팔로우 / 팔로잉 토글 버튼 (btnFollowToggle)
//        binding.btnFollowToggle.setOnClickListener {
//            toggleFollow()
//        }
//
//        loadProfileData()
//    }
//
//    // 프로필 데이터 수집 및 UI 동기화
//    private fun loadProfileData() {
//        val uid = targetUserId ?: return
//        val myUid = auth.currentUser?.uid ?: ""
//
//        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val userDoc = firestore.collection("users").document(uid).get().await()
//                val followersSnapshot = firestore.collection("users").document(uid).collection("followers").get().await()
//                val followingSnapshot = firestore.collection("users").document(uid).collection("following").get().await()
//
//                val isFollowingDoc = firestore.collection("users").document(myUid)
//                    .collection("following").document(uid).get().await()
//
//                val userCode = userDoc.getString("userCode") ?: ""
//                val nickname = userDoc.getString("nickname") ?: ""
//                val profileImageUrl = userDoc.getString("profileImageUrl")
//
//                withContext(Dispatchers.Main) {
//                    binding.tvUserCodeTop.text = "#$userCode"
//                    binding.tvNickname.text = nickname
//                    binding.tvFollowerCount.text = "팔로워 ${followersSnapshot.documents.size}"
//                    binding.tvFollowingCount.text = "팔로잉 ${followingSnapshot.documents.size}"
//
//                    if (isFollowingDoc.exists()) {
//                        binding.btnFollowToggle.text = "팔로잉 중"
//                    } else {
//                        binding.btnFollowToggle.text = "팔로우"
//                    }
//
//                    binding.ivProfileImage.load(profileImageUrl) {
//                        crossfade(true)
//                        placeholder(R.drawable.ic_default_profile)
//                        error(R.drawable.ic_default_profile)
//                        transformations(CircleCropTransformation())
//                    }
//                }
//            } catch (_: Exception) { }
//        }
//    }
//
//    // 팔로우 / 언팔로우 처리
//    private fun toggleFollow() {
//        val uid = targetUserId ?: return
//        val myUid = auth.currentUser?.uid ?: return
//
//        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val myFollowingRef = firestore.collection("users").document(myUid).collection("following").document(uid)
//                val targetFollowerRef = firestore.collection("users").document(uid).collection("followers").document(myUid)
//
//                val doc = myFollowingRef.get().await()
//                if (doc.exists()) {
//                    myFollowingRef.delete().await()
//                    targetFollowerRef.delete().await()
//                } else {
//                    val data = mapOf("createdAt" to System.currentTimeMillis())
//                    myFollowingRef.set(data).await()
//                    targetFollowerRef.set(data).await()
//                }
//
//                loadProfileData()
//            } catch (_: Exception) { }
//        }
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
// }