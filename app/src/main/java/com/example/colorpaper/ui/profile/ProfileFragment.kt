package com.example.colorpaper.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.colorpaper.ui.diary.FriendDiaryDetailFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.colorpaper.R
import com.example.colorpaper.ui.diary.DiaryDetailFragment
import com.example.colorpaper.ui.flashcard.FlashcardStudyFragment
import com.example.colorpaper.ui.friend.FriendListFragment
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 팝업 리스트용 데이터 모델
data class PopupUser(
    val uid: String,
    val nickname: String,
    val profileImageUrl: String? = null
)

class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private var profileFlashcardAdapter: ProfileFlashcardAdapter? = null
    private var popupFriendAdapter: PopupFriendAdapter? = null

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var targetUserId: String? = null
    private var isMyProfile: Boolean = true
    private var isSharedDiaryClicked = false

    companion object {
        private const val ARG_TARGET_USER_ID = "TARGET_USER_ID"

        fun newInstance(targetUserId: String? = null): ProfileFragment {
            return ProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_USER_ID, targetUserId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUserId = arguments?.getString(ARG_TARGET_USER_ID)
        val currentUid = auth.currentUser?.uid

        isMyProfile = targetUserId.isNullOrEmpty() || targetUserId == currentUid
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val tvUserCodeTop = view.findViewById<TextView>(R.id.tvUserCodeTop)
        val ivSearchFriend = view.findViewById<ImageView>(R.id.ivSearchFriend)
        val ivEditProfile = view.findViewById<ImageView>(R.id.ivEditProfile)
        val ivProfileImage = view.findViewById<ImageView>(R.id.ivProfileImage)
        val tvNickname = view.findViewById<TextView>(R.id.tvNickname)
        val tvFollowerCount = view.findViewById<TextView>(R.id.tvFollowerCount)
        val tvFollowingCount = view.findViewById<TextView>(R.id.tvFollowingCount)
        val tvFriendUpdateBadge = view.findViewById<TextView>(R.id.tvFriendUpdateBadge)
        val llSharedDiary = view.findViewById<LinearLayout>(R.id.llSharedDiary)
        val btnFollowToggle = view.findViewById<TextView?>(R.id.btnFollowToggle)
        val btnLogout = view.findViewById<TextView?>(R.id.btnLogout)

        val cardEmptySharedFlashcard = view.findViewById<MaterialCardView>(R.id.cardEmptySharedFlashcard)
        val rvSharedFlashcards = view.findViewById<RecyclerView>(R.id.rvSharedFlashcards)
        val rvHighlights = view.findViewById<RecyclerView>(R.id.rvHighlights)

        val flFriendListPopup = view.findViewById<View>(R.id.flFriendListPopup)
        val cardPopupContent = view.findViewById<View>(R.id.cardPopupContent)
        val tvPopupTitle = view.findViewById<TextView>(R.id.tvPopupTitle)
        val ivClosePopup = view.findViewById<ImageView>(R.id.ivClosePopup)
        val rvPopupFriendList = view.findViewById<RecyclerView>(R.id.rvPopupFriendList)

        // UI 분기 처리
        ivEditProfile?.isVisible = isMyProfile
        tvFriendUpdateBadge?.isVisible = isMyProfile
        ivSearchFriend?.isVisible = isMyProfile
        btnFollowToggle?.isVisible = !isMyProfile
        btnLogout?.isVisible = isMyProfile

        // 🌟 1. 팝업 리사이클러뷰 어댑터 세팅
        popupFriendAdapter = PopupFriendAdapter(emptyList()) { user ->
            flFriendListPopup?.visibility = View.GONE
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, newInstance(user.uid))
                .addToBackStack(null)
                .commit()
        }
        rvPopupFriendList?.layoutManager = LinearLayoutManager(context)
        rvPopupFriendList?.adapter = popupFriendAdapter

        // 🌟 2. 공유 단어장 어댑터 세팅
        rvSharedFlashcards?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        profileFlashcardAdapter = ProfileFlashcardAdapter(
            setList = emptyList(),
            onStartClick = { folder ->
                val bundle = Bundle().apply {
                    putInt("SET_ID", folder.folderId)
                    putString("SET_TITLE", folder.folderName)
                }
                val studyFragment = FlashcardStudyFragment().apply {
                    arguments = bundle
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, studyFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onItemLongClick = { folder ->
                Toast.makeText(context, "'${folder.folderName}' 단어장", Toast.LENGTH_SHORT).show()
            }
        )
        rvSharedFlashcards?.adapter = profileFlashcardAdapter

        // 🌟 3. 데이터 로딩 최초 트리거 실행 (가장 중요!)
        loadAllProfileData()

        // 🌟 4. 유저 프로필 정보 관찰
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                tvNickname?.text = user.nickname
                tvUserCodeTop?.text = "#${user.userCode}"

                ivProfileImage?.load(user.profileImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_default_profile)
                    error(R.drawable.ic_default_profile)
                    transformations(CircleCropTransformation())
                }
            }

            btnLogout?.setOnClickListener {
                performLogout()
            }
        }

        // 팔로워/팔로잉 카운트 및 상태 세팅
        val effectiveUid = if (isMyProfile) auth.currentUser?.uid else targetUserId
        effectiveUid?.let {
            loadFollowCounts(it, tvFollowerCount, tvFollowingCount)
            if (!isMyProfile && btnFollowToggle != null) {
                checkFollowStatus(it, btnFollowToggle)
            }
        }

        // 🌟 5. 단어장 라이브데이터 관찰
        viewModel.sharedFolders.observe(viewLifecycleOwner) { folders ->
            if (folders.isNullOrEmpty()) {
                cardEmptySharedFlashcard?.visibility = View.VISIBLE
                rvSharedFlashcards?.visibility = View.GONE
            } else {
                cardEmptySharedFlashcard?.visibility = View.GONE
                rvSharedFlashcards?.visibility = View.VISIBLE
                profileFlashcardAdapter?.updateData(folders)
            }
        }

        // 🌟 6. 하이라이트 세팅 (하드코딩 완전히 제거 및 생성될 때마다 실시간 연동)
        viewModel.highlights.observe(viewLifecycleOwner) { highlights ->
            val list = highlights ?: emptyList()
            rvHighlights?.adapter = HighlightAdapter(
                items = list,
                onItemClick = { item ->
                    navigateToDiaryDetail(item.date, item.diaryId)
                }
            )
        }

// 🌟 7. 공개 다이어리 관찰 부분
        viewModel.publicDiaries.observe(viewLifecycleOwner) { diaries ->
            if (isSharedDiaryClicked) {
                isSharedDiaryClicked = false
                if (diaries.isNullOrEmpty()) {
                    Toast.makeText(context, "공개 중인 다이어리가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val latestDiary = diaries.last()
                    val targetDateStr = latestDiary.createdAt.toString()

                    // diaryId 함께 전달
                    navigateToDiaryDetail(targetDateStr, latestDiary.diaryId)
                }
            }
        }

        // 클릭 이벤트 등록
        tvFollowerCount?.setOnClickListener {
            tvPopupTitle?.text = "팔로워 목록"
            flFriendListPopup?.visibility = View.VISIBLE
            effectiveUid?.let { uid -> fetchFollowData(uid, "followers") }
        }

        tvFollowingCount?.setOnClickListener {
            tvPopupTitle?.text = "팔로잉 목록"
            flFriendListPopup?.visibility = View.VISIBLE
            effectiveUid?.let { uid -> fetchFollowData(uid, "following") }
        }

        tvUserCodeTop?.setOnClickListener {
            val userCode = tvUserCodeTop.text.toString().removePrefix("#")
            if (userCode.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UserCode", userCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "유저 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnFollowToggle?.setOnClickListener {
            effectiveUid?.let { targetUid ->
                toggleFollow(targetUid, btnFollowToggle, tvFollowerCount, tvFollowingCount)
            }
        }

        ivClosePopup?.setOnClickListener { flFriendListPopup?.visibility = View.GONE }
        flFriendListPopup?.setOnClickListener { flFriendListPopup.visibility = View.GONE }
        cardPopupContent?.setOnClickListener { }

        ivBack?.setOnClickListener { parentFragmentManager.popBackStack() }

        ivEditProfile?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileEditFragment())
                .addToBackStack(null)
                .commit()
        }

        ivSearchFriend?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FriendListFragment.newInstance(null, "SEARCH"))
                .addToBackStack(null)
                .commit()
        }

        tvFriendUpdateBadge?.setOnClickListener {
            Toast.makeText(context, "새로 올라온 친구들의 다이어리를 띄웁니다!", Toast.LENGTH_SHORT).show()
        }

        cardEmptySharedFlashcard?.setOnClickListener {
            viewModel.fetchMySharedFolders(targetUserId)
            Toast.makeText(context, "공유 단어장 목록을 새로고침했습니다.", Toast.LENGTH_SHORT).show()
        }

        llSharedDiary?.setOnClickListener {
            isSharedDiaryClicked = true
            viewModel.fetchPublicDiaries(targetUserId)
        }
    }

    // 💡 백엔드/ViewModel 호출 함수 정리
    private fun loadAllProfileData() {
        viewModel.fetchUserProfile(targetUserId)
        viewModel.fetchMySharedFolders(targetUserId)
        viewModel.fetchHighlights(targetUserId) // 🔥 하이라이트 데이터 요청 추가
    }

    private fun checkFollowStatus(targetUid: String, btnToggle: TextView) {
        val myUid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = firestore.collection("users").document(myUid)
                    .collection("following").document(targetUid).get().await()

                withContext(Dispatchers.Main) {
                    btnToggle.text = if (doc.exists()) "팔로우 취소" else "팔로우"
                }
            } catch (_: Exception) { }
        }
    }

    private fun performLogout() {
        auth.signOut()
        Toast.makeText(requireContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.example.colorpaper.ui.login.LoginFragment())
            .commit()
    }

    private fun toggleFollow(targetUid: String, btnToggle: TextView, tvFollower: TextView?, tvFollowing: TextView?) {
        val myUid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val myFollowingRef = firestore.collection("users").document(myUid).collection("following").document(targetUid)
                val targetFollowerRef = firestore.collection("users").document(targetUid).collection("followers").document(myUid)

                val doc = myFollowingRef.get().await()
                if (doc.exists()) {
                    myFollowingRef.delete().await()
                    targetFollowerRef.delete().await()
                } else {
                    val data = mapOf("createdAt" to System.currentTimeMillis())
                    myFollowingRef.set(data).await()
                    targetFollowerRef.set(data).await()
                }

                withContext(Dispatchers.Main) {
                    checkFollowStatus(targetUid, btnToggle)
                    loadFollowCounts(targetUid, tvFollower, tvFollowing)
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadFollowCounts(userId: String, tvFollower: TextView?, tvFollowing: TextView?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val followersCount = firestore.collection("users").document(userId).collection("followers").get().await().size()
                val followingCount = firestore.collection("users").document(userId).collection("following").get().await().size()

                withContext(Dispatchers.Main) {
                    tvFollower?.text = "팔로워 $followersCount"
                    tvFollowing?.text = "팔로잉 $followingCount"
                }
            } catch (_: Exception) { }
        }
    }

    private fun fetchFollowData(userId: String, subCollection: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("users").document(userId)
                    .collection(subCollection).get().await()

                val userList = mutableListOf<PopupUser>()

                for (doc in snapshot.documents) {
                    val targetUid = doc.id
                    val userDoc = firestore.collection("users").document(targetUid).get().await()
                    if (userDoc.exists()) {
                        val nickname = userDoc.getString("nickname") ?: "알 수 없음"
                        val profileImageUrl = userDoc.getString("profileImageUrl")
                        userList.add(PopupUser(targetUid, nickname, profileImageUrl))
                    }
                }

                withContext(Dispatchers.Main) {
                    popupFriendAdapter?.updateList(userList)
                    if (userList.isEmpty()) {
                        Toast.makeText(context, "목록이 비어 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "데이터를 불러 오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToDiaryDetail(targetDate: String, diaryId: Int = 0) {
        val effectiveUid = if (isMyProfile) auth.currentUser?.uid else targetUserId

        val fragment: Fragment = if (isMyProfile) {
            // 1. 내 프로필인 경우 -> 내 일기 상세 화면
            DiaryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("TARGET_DATE", targetDate)
                    putString("TARGET_USER_ID", effectiveUid)
                    putInt("DIARY_ID", diaryId)
                }
            }
        } else {
            // 2. 친구 프로필인 경우 -> 날짜(targetDate) 포함 전달 🌟
            FriendDiaryDetailFragment.newInstance(
                targetUserId = effectiveUid ?: "",
                diaryId = diaryId,
                targetDate = targetDate
            )
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        loadAllProfileData()
    }
}

class PopupFriendAdapter(
    private var list: List<PopupUser>,
    private val onItemClick: (PopupUser) -> Unit
) : RecyclerView.Adapter<PopupFriendAdapter.ViewHolder>() {

    fun updateList(newList: List<PopupUser>) {
        this.list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_popup_friend, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvNickname.text = item.nickname
        holder.ivProfile.load(item.profileImageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_default_profile)
            error(R.drawable.ic_default_profile)
            transformations(CircleCropTransformation())
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivPopupProfile)
        val tvNickname: TextView = view.findViewById(R.id.tvPopupNickname)
    }
}