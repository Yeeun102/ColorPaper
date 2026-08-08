package com.example.colorpaper.ui.friend

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.colorpaper.data.model.FriendEntity
import com.example.colorpaper.data.model.UserEntity
import kotlinx.coroutines.launch

class FriendListFragment : Fragment() {

    private lateinit var adapter: FriendSearchAdapter
    private val myUserId = 1 // 내 ID (테스트용 고정 값)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_friend_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val ivSearch = view.findViewById<ImageView>(R.id.ivSearch)
        val rvFriendList = view.findViewById<RecyclerView>(R.id.rvFriendList)

        val db = AppDatabase.getDatabase(requireContext())

        // 1. 뒤로가기
        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. 리사이클러뷰 & 어댑터 초기화
        adapter = FriendSearchAdapter(
            friendList = emptyList(),
            onFollowClick = { targetUser ->
                // [팔로우 처리] -> friend_table 에 추가
                viewLifecycleOwner.lifecycleScope.launch {
                    val newFriendRelation = FriendEntity(
                        myUserId = myUserId,
                        friendUserId = targetUser.userId
                    )
                    db.userDao().addFriend(newFriendRelation)
                    Toast.makeText(requireContext(), "${targetUser.nickname}님을 팔로우했습니다!", Toast.LENGTH_SHORT).show()

                    // 팔로우 후 리스트에서 제거/갱신
                    performSearch(db, etSearch.text.toString().trim())
                }
            },
            onItemClick = { targetUser ->
                // [7.1.2 친구 프로필 홈으로 이동]
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, FriendProfileFragment.newInstance(targetUser.userId))
                    .addToBackStack(null)
                    .commit()
            }
        )

        rvFriendList.layoutManager = LinearLayoutManager(requireContext())
        rvFriendList.adapter = adapter

        // 3. 돋보기 버튼 클릭 검색
        ivSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            performSearch(db, query)
        }

        // 4. 검색창 실시간 입력 검색 (텍스트 타이핑할 때마다 바로 조회)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(db, s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 화면 처음 열었을 때는 전체 안 팔로우 유저 조회
        performSearch(db, "")
    }

    private fun performSearch(db: AppDatabase, query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // UserDao의 searchNewFriends 쿼리 실행
            val searchResults = db.userDao().searchNewFriends(myUserId, query)
            adapter.updateList(searchResults)
        }
    }
}