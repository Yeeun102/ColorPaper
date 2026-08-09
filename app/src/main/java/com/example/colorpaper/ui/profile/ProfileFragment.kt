package com.example.colorpaper.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
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
import com.example.colorpaper.ui.theme.ProfileThemeStyler
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

        val safeContext = context?.applicationContext ?: return

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
        val diaryBookPreview = view.findViewById<View>(R.id.diaryBookPreview)
        val diaryBookCoverLayer = view.findViewById<View>(R.id.diaryBookCoverLayer)
        val diaryBookFlap = view.findViewById<View>(R.id.diaryBookFlap)
        val btnFollowToggle = view.findViewById<TextView?>(R.id.btnFollowToggle)
        val btnLogout = view.findViewById<TextView?>(R.id.btnLogout)

        val cardEmptySharedFlashcard = view.findViewById<MaterialCardView>(R.id.cardEmptySharedFlashcard)
        val rvSharedFlashcards = view.findViewById<RecyclerView>(R.id.rvSharedFlashcards)
        val rvHighlights = view.findViewById<RecyclerView>(R.id.rvHighlights)
        val dividerHighlightsTop = view.findViewById<View>(R.id.dividerHighlightsTop)
        val dividerHighlightsBottom = view.findViewById<View>(R.id.dividerHighlightsBottom)

        val flFriendListPopup = view.findViewById<View>(R.id.flFriendListPopup)
        val cardPopupContent = view.findViewById<View>(R.id.cardPopupContent)
        val tvPopupTitle = view.findViewById<TextView>(R.id.tvPopupTitle)
        val ivClosePopup = view.findViewById<ImageView>(R.id.ivClosePopup)
        val rvPopupFriendList = view.findViewById<RecyclerView>(R.id.rvPopupFriendList)

        fun showFriendPopup() {
            flFriendListPopup.visibility = View.VISIBLE
            flFriendListPopup.alpha = 0f
            cardPopupContent.scaleX = 0.88f
            cardPopupContent.scaleY = 0.88f
            flFriendListPopup.animate().alpha(1f).setDuration(150L).start()
            cardPopupContent.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300L)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }

        fun hideFriendPopup() {
            cardPopupContent.animate().cancel()
            flFriendListPopup.animate().cancel()
            cardPopupContent.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(120L)
                .setInterpolator(AccelerateInterpolator())
                .start()
            flFriendListPopup.animate()
                .alpha(0f)
                .setDuration(140L)
                .withEndAction {
                    flFriendListPopup.visibility = View.GONE
                    flFriendListPopup.alpha = 1f
                    cardPopupContent.scaleX = 1f
                    cardPopupContent.scaleY = 1f
                }
                .start()
        }

        // UI 분기 처리
        ivEditProfile?.isVisible = isMyProfile
        tvFriendUpdateBadge?.isVisible = isMyProfile
        ivSearchFriend?.isVisible = isMyProfile
        btnFollowToggle?.isVisible = !isMyProfile
        btnLogout?.isVisible = isMyProfile

        // 🌟 1. 팝업 리사이클러뷰 어댑터 세팅
        popupFriendAdapter = PopupFriendAdapter(emptyList()) { user ->
            hideFriendPopup()
            softTransaction()
                .replace(R.id.fragment_container, newInstance(user.uid))
                .addToBackStack(null)
                .commit()
        }
        rvPopupFriendList?.layoutManager = LinearLayoutManager(safeContext)
        rvPopupFriendList?.adapter = popupFriendAdapter

        // 🌟 2. 공유 단어장 어댑터 세팅
        rvSharedFlashcards?.layoutManager = LinearLayoutManager(safeContext, LinearLayoutManager.HORIZONTAL, false)
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
                softTransaction()
                    .replace(R.id.fragment_container, studyFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onItemLongClick = { folder ->
                Toast.makeText(context, "'${folder.folderName}' 단어장", Toast.LENGTH_SHORT).show()
            }
        )
        rvSharedFlashcards?.adapter = profileFlashcardAdapter

        // 🌟 3. 데이터 로딩 최초 트리거 실행
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

        // 🌟 6. 하이라이트 세팅
        viewModel.highlights.observe(viewLifecycleOwner) { highlights ->
            val list = highlights ?: emptyList()
            val hasHighlights = list.isNotEmpty()

            dividerHighlightsTop?.visibility = if (hasHighlights) View.VISIBLE else View.GONE
            dividerHighlightsBottom?.visibility = View.VISIBLE
            rvHighlights?.visibility = if (hasHighlights) View.VISIBLE else View.GONE

            rvHighlights?.adapter = HighlightAdapter(
                items = list,
                onItemClick = { item ->
                    navigateToDiaryDetail(item.date, item.diaryId, readOnly = true)
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
                    // 💡 Date().toString()은 java.util.Date.parse()에서 인식 못 할 수 있으므로
                    // 일관된 yyyy-MM-dd 형식으로 변환하여 전달
                    val targetDateStr = latestDiary.createdAt

                    // diaryId 함께 전달
                    openDiaryWithBookAnimation(
                        diaryBookPreview,
                        diaryBookCoverLayer,
                        diaryBookFlap
                    ) {
                        if (isAdded) {
                            navigateToDiaryDetail(
                                targetDateStr,
                                latestDiary.diaryId,
                                bookTransition = true
                            )
                        }
                    }
                }
            }
        }

        // 클릭 이벤트 등록
        tvFollowerCount?.setOnClickListener {
            tvPopupTitle?.text = "팔로워 목록"
            showFriendPopup()
            effectiveUid?.let { uid -> fetchFollowData(uid, "followers") }
        }

        tvFollowingCount?.setOnClickListener {
            tvPopupTitle?.text = "팔로잉 목록"
            showFriendPopup()
            effectiveUid?.let { uid -> fetchFollowData(uid, "following") }
        }

        tvUserCodeTop?.setOnClickListener {
            val userCode = tvUserCodeTop.text.toString().removePrefix("#")
            if (userCode.isNotBlank()) {
                val currentContext = context ?: return@setOnClickListener
                val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UserCode", userCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(currentContext, "유저 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnFollowToggle?.setOnClickListener {
            effectiveUid?.let { targetUid ->
                toggleFollow(targetUid, btnFollowToggle, tvFollowerCount, tvFollowingCount)
            }
        }

        ivClosePopup?.setOnClickListener { hideFriendPopup() }
        flFriendListPopup?.setOnClickListener { hideFriendPopup() }
        cardPopupContent?.setOnClickListener { }

        ivBack?.setOnClickListener { parentFragmentManager.popBackStack() }

        ivEditProfile?.setOnClickListener {
            softTransaction()
                .replace(R.id.fragment_container, ProfileEditFragment())
                .addToBackStack(null)
                .commit()
        }

        ivSearchFriend?.setOnClickListener {
            softTransaction()
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
        viewModel.fetchHighlights(targetUserId)
    }

    private fun checkFollowStatus(targetUid: String, btnToggle: TextView) {
        val myUid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val isFollowing = withContext(Dispatchers.IO) {
                    val doc = firestore.collection("users").document(myUid)
                        .collection("following").document(targetUid).get().await()
                    doc.exists()
                }

                if (!isAdded || view == null) return@launch
                btnToggle.text = if (isFollowing) "팔로우 취소" else "팔로우"

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("ProfileFragment", "Follow Status Check Error", e)
                }
            }
        }
    }

    private fun performLogout() {
        val safeContext = context?.applicationContext ?: return
        auth.signOut()
        Toast.makeText(safeContext, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

        softTransaction()
            .replace(R.id.fragment_container, com.example.colorpaper.ui.login.LoginFragment())
            .commit()
    }

    private fun toggleFollow(targetUid: String, btnToggle: TextView, tvFollower: TextView?, tvFollowing: TextView?) {
        val myUid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
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
                }

                if (!isAdded || view == null) return@launch
                checkFollowStatus(targetUid, btnToggle)
                loadFollowCounts(targetUid, tvFollower, tvFollowing)

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("ProfileFragment", "Toggle Follow Error", e)
                }
            }
        }
    }

    private fun loadFollowCounts(userId: String, tvFollower: TextView?, tvFollowing: TextView?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (followersCount, followingCount) = withContext(Dispatchers.IO) {
                    val followers = firestore.collection("users").document(userId).collection("followers").get().await().size()
                    val following = firestore.collection("users").document(userId).collection("following").get().await().size()
                    Pair(followers, following)
                }

                if (!isAdded || view == null) return@launch
                tvFollower?.text = "팔로워 $followersCount"
                tvFollowing?.text = "팔로잉 $followingCount"

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("ProfileFragment", "Load Follow Counts Error", e)
                }
            }
        }
    }

    // 💡 async 병렬 처리를 통한 팔로워/팔로잉 팝업 리스트 최적화
    private fun fetchFollowData(userId: String, subCollection: String) {
        val safeContext = context?.applicationContext ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userList = withContext(Dispatchers.IO) {
                    val snapshot = firestore.collection("users").document(userId)
                        .collection(subCollection).get().await()

                    // async 병렬 호출로 Firestore 문서들을 동시에 가져와 속도 향상
                    coroutineScope {
                        val deferredUsers = snapshot.documents.map { doc ->
                            async {
                                val targetUid = doc.id
                                val userDoc = firestore.collection("users").document(targetUid).get().await()
                                if (userDoc.exists()) {
                                    val nickname = userDoc.getString("nickname") ?: "알 수 없음"
                                    val profileImageUrl = userDoc.getString("profileImageUrl")
                                    PopupUser(targetUid, nickname, profileImageUrl)
                                } else {
                                    null
                                }
                            }
                        }
                        deferredUsers.mapNotNull { it.await() }
                    }
                }

                if (!isAdded || view == null) return@launch
                popupFriendAdapter?.updateList(userList)
                if (userList.isEmpty()) {
                    Toast.makeText(safeContext, "목록이 비어 있습니다.", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                if (e !is CancellationException) {
                    Toast.makeText(safeContext, "데이터를 불러 오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToDiaryDetail(
        targetDate: String,
        diaryId: Int = 0,
        readOnly: Boolean = false,
        bookTransition: Boolean = false
    ) {
        val effectiveUid = if (isMyProfile) auth.currentUser?.uid else targetUserId

        val fragment: Fragment = if (isMyProfile) {
            // 1. 내 프로필인 경우 -> 내 일기 상세 화면
            DiaryDetailFragment.newInstance(
                targetDate = targetDate,
                targetUserId = effectiveUid,
                readOnly = readOnly
            )
        } else {
            // 2. 친구 프로필인 경우 -> 날짜(targetDate) 포함 전달
            FriendDiaryDetailFragment.newInstance(
                targetUserId = effectiveUid ?: "",
                diaryId = diaryId,
                targetDate = targetDate
            )
        }

        val transaction = if (bookTransition) bookOpenTransaction() else softTransaction()
        transaction
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        loadAllProfileData()
    }

    private fun openDiaryWithBookAnimation(
        preview: View,
        coverLayer: View,
        flap: View,
        onOpened: () -> Unit
    ) {
        if (preview.getTag(R.id.diaryBookPreview) == true) return
        preview.setTag(R.id.diaryBookPreview, true)
        val density = resources.displayMetrics.density
        val paperLayer = preview.findViewById<View>(R.id.diaryBookPaperLayer)
        allowBookToDrawOutside(preview)
        preview.bringToFront()
        coverLayer.bringToFront()
        flap.bringToFront()

        preview.animate().cancel()
        coverLayer.animate().cancel()
        flap.animate().cancel()
        paperLayer.animate().cancel()
        coverLayer.pivotX = 0f
        coverLayer.pivotY = coverLayer.height / 2f
        coverLayer.cameraDistance = 9000f * density
        flap.pivotX = flap.width.toFloat()
        flap.pivotY = flap.height / 2f
        flap.cameraDistance = 9000f * density

        preview.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .translationY(-10f * density)
            .setDuration(260L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()

        flap.animate()
            .rotationY(105f)
            .translationX(5f * density)
            .setDuration(250L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        paperLayer.animate()
            .translationX(-8f * density)
            .setStartDelay(260L)
            .setDuration(400L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        coverLayer.animate()
            .rotationY(-155f)
            .translationX(0f)
            .setStartDelay(235L)
            .setDuration(470L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                preview.animate()
                    .scaleX(1.55f)
                    .scaleY(1.55f)
                    .setDuration(210L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        preview.scaleX = 1f
                        preview.scaleY = 1f
                        preview.translationY = 0f
                        coverLayer.rotationY = 0f
                        coverLayer.translationX = 0f
                        flap.rotationY = 0f
                        flap.translationX = 5f * density
                        paperLayer.translationX = 0f
                        preview.setTag(R.id.diaryBookPreview, false)
                        onOpened()
                    }
                    .start()
            }
            .start()
    }

    private fun allowBookToDrawOutside(preview: View) {
        var ancestor = preview.parent
        while (ancestor is ViewGroup) {
            val group = ancestor
            group.clipChildren = false
            group.clipToPadding = false
            if (group.id == R.id.fragment_container) break
            ancestor = group.parent
        }
    }

    private fun softTransaction() = parentFragmentManager.beginTransaction()
        .setCustomAnimations(
            R.animator.screen_morph_enter,
            R.animator.screen_morph_exit,
            R.animator.screen_morph_enter,
            R.animator.screen_morph_exit
        )

    private fun bookOpenTransaction() = parentFragmentManager.beginTransaction()
        .setCustomAnimations(
            R.animator.book_open_enter,
            R.animator.book_open_exit,
            R.animator.screen_morph_enter,
            R.animator.screen_morph_exit
        )
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
        ProfileThemeStyler.applyItem(holder.itemView)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivPopupProfile)
        val tvNickname: TextView = view.findViewById(R.id.tvPopupNickname)
    }
}
