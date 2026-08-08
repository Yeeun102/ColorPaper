package com.example.colorpaper.ui.friend

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.repository.UserRepository
import com.example.colorpaper.ui.profile.ProfileFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FriendListFragment : Fragment() {

    private lateinit var adapter: FriendSearchAdapter
    private lateinit var userRepository: UserRepository
    private lateinit var db: AppDatabase

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var targetUserId: String? = null
    private var mode: String = "SEARCH"
    private var searchJob: Job? = null

    companion object {
        private const val ARG_TARGET_USER_ID = "TARGET_USER_ID"
        private const val ARG_MODE = "MODE"

        fun newInstance(targetUserId: String? = null, mode: String = "SEARCH"): FriendListFragment {
            return FriendListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_USER_ID, targetUserId)
                    putString(ARG_MODE, mode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            targetUserId = it.getString(ARG_TARGET_USER_ID)
            mode = it.getString(ARG_MODE, "SEARCH")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_friend_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 프래그먼트 이탈 대비 safeContext 확보
        val safeContext = context?.applicationContext ?: return

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val ivSearch = view.findViewById<ImageView>(R.id.ivSearch)
        val rvFriendList = view.findViewById<RecyclerView>(R.id.rvFriendList)

        db = AppDatabase.getDatabase(safeContext)
        userRepository = UserRepository(db)

        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 💡 어댑터 설정 (Firestore 팔로우/취소 토글 및 위치 갱신)
        adapter = FriendSearchAdapter(
            friendList = emptyList(),
            onFollowClick = { targetUser, position ->
                toggleFollowInSearch(targetUser, position)
            },
            onItemClick = { targetUser ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ProfileFragment.newInstance(targetUser.userId))
                    .addToBackStack(null)
                    .commit()
            }
        )

        rvFriendList.layoutManager = LinearLayoutManager(safeContext)
        rvFriendList.adapter = adapter

        ivSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            performSearch(query)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        performSearch("")
    }

    // 💡 Firestore 기반 검색 및 팔로우 상태/카운트 조회 (속도 최적화 + 안전성 검증)
    private fun performSearch(query: String) {
        searchJob?.cancel()

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val myUid = auth.currentUser?.uid ?: return@launch

            try {
                // 1. IO 스레드에서 유저 검색 및 async를 통한 비동기 병렬 처리
                val uiModels = withContext(Dispatchers.IO) {
                    val userEntities = userRepository.searchUsers(query)

                    userEntities.map { user ->
                        coroutineScope {
                            val targetUid = user.email // Firestore UID

                            // 각 Firestore 통신을 동시에 병렬 요청 (검색 속도 극대화)
                            val isFollowingDeferred = async {
                                firestore.collection("users").document(myUid)
                                    .collection("following").document(targetUid).get().await().exists()
                            }
                            val followerCountDeferred = async {
                                firestore.collection("users").document(targetUid)
                                    .collection("followers").get().await().size()
                            }
                            val followingCountDeferred = async {
                                firestore.collection("users").document(targetUid)
                                    .collection("following").get().await().size()
                            }

                            FriendUiModel(
                                userId = targetUid,
                                userCode = user.userCode,
                                nickname = user.nickname,
                                profileImageUrl = user.profileImageUrl,
                                followerCount = followerCountDeferred.await(),
                                followingCount = followingCountDeferred.await(),
                                isFollowing = isFollowingDeferred.await()
                            )
                        }
                    }
                }

                // 2. UI 반영 전 화면 분리(isAdded) 및 뷰 파괴 검증
                if (!isAdded || view == null) return@launch

                // 3. 어댑터 갱신
                adapter.updateList(uiModels)

            } catch (e: Exception) {
                // 검색 취소(Job Cancel)로 인한 예외가 아닐 경우에만 로그 출력
                if (e !is CancellationException) {
                    Log.e("FriendListFragment", "검색 로직 에러", e)
                }
            }
        }
    }

    // 💡 + / X 클릭 시 Firestore 토글 및 단일 아이템 갱신
    private fun toggleFollowInSearch(targetUser: FriendUiModel, position: Int) {
        val safeContext = context?.applicationContext ?: return
        val myUid = auth.currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val myFollowingRef = firestore.collection("users").document(myUid).collection("following").document(targetUser.userId)
                    val targetFollowerRef = firestore.collection("users").document(targetUser.userId).collection("followers").document(myUid)

                    if (targetUser.isFollowing) {
                        // 팔로우 취소
                        myFollowingRef.delete().await()
                        targetFollowerRef.delete().await()
                        targetUser.isFollowing = false
                        targetUser.followerCount = (targetUser.followerCount - 1).coerceAtLeast(0)
                    } else {
                        // 팔로우 추가
                        val data = mapOf("createdAt" to System.currentTimeMillis())
                        myFollowingRef.set(data).await()
                        targetFollowerRef.set(data).await()
                        targetUser.isFollowing = true
                        targetUser.followerCount += 1
                    }
                }

                // 화면 탈퇴 시 UI 변경/토스트 생략
                if (!isAdded || view == null) return@launch

                val msg = if (targetUser.isFollowing) {
                    "${targetUser.nickname}님을 팔로우했습니다."
                } else {
                    "${targetUser.nickname}님 팔로우를 취소했습니다."
                }

                Toast.makeText(safeContext, msg, Toast.LENGTH_SHORT).show()
                adapter.notifyItemChanged(position)

            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                Toast.makeText(safeContext, "처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}