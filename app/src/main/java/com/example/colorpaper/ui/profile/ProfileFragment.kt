package com.example.colorpaper.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.colorpaper.R
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.ui.diary.DiaryDetailFragment
import com.example.colorpaper.ui.friend.FriendListFragment
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private var sharedFlashcardAdapter: SharedFlashcardAdapter? = null

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
        val tvFriendUpdateBadge = view.findViewById<TextView>(R.id.tvFriendUpdateBadge)
        val llSharedDiary = view.findViewById<LinearLayout>(R.id.llSharedDiary)

        // 🌟 공유 단어장 뷰 요소 (빈 상태 카드 & RecyclerView)
        val cardEmptySharedFlashcard = view.findViewById<MaterialCardView>(R.id.cardEmptySharedFlashcard)
        val rvSharedFlashcards = view.findViewById<RecyclerView>(R.id.rvSharedFlashcards)

        val rvHighlights = view.findViewById<RecyclerView>(R.id.rvHighlights)

        // 공유 단어장 RecyclerView 가로 스크롤 설정
        rvSharedFlashcards?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // 1. 유저 프로필 정보 관찰
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                tvNickname.text = user.nickname
                tvUserCodeTop.text = "#${user.userCode}"

                ivProfileImage.load(user.profileImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_default_profile)
                    error(R.drawable.ic_default_profile)
                    transformations(CircleCropTransformation())
                }
            }
        }

        // 🌟 2. FolderEntity 데이터 동적 관찰
        viewModel.sharedFolders.observe(viewLifecycleOwner) { folders ->
            if (folders.isNullOrEmpty()) {
                // 공유 중인 FolderEntity가 없으면 연핑크회색 안내 카드 노출
                cardEmptySharedFlashcard?.visibility = View.VISIBLE
                rvSharedFlashcards?.visibility = View.GONE
            } else {
                // FolderEntity가 존재하면 안내 카드 숨기고 목록 표시
                cardEmptySharedFlashcard?.visibility = View.GONE
                rvSharedFlashcards?.visibility = View.VISIBLE

                sharedFlashcardAdapter = SharedFlashcardAdapter(folders) { folder ->
                    Toast.makeText(context, "${folder.folderName} 단어장으로 이동합니다.", Toast.LENGTH_SHORT).show()
                }
                rvSharedFlashcards?.adapter = sharedFlashcardAdapter
            }
        }

        // 🌟 3. 하이라이트 (원형 썸네일 날짜 MM/dd 표시)
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        fun formatDateForThumbnail(dateStr: String): String {
            return try {
                val date = inputFormat.parse(dateStr)
                if (date != null) displayFormat.format(date) else dateStr
            } catch (e: Exception) {
                dateStr
            }
        }

        val todayStr = inputFormat.format(Date())
        val rawHighlights = listOf(
            HighlightItem("1", formatDateForThumbnail(todayStr)),
            HighlightItem("2", formatDateForThumbnail("2026-08-05"))
        )
        val uniqueHighlights = rawHighlights.distinctBy { it.date }

        /*
        val highlightAdapter = HighlightAdapter(
            items = uniqueHighlights,
            onItemClick = { item ->
                val detailFragment = DiaryDetailFragment.newInstance(item.date, isEditMode = false)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onMoreClick = {
                Toast.makeText(context, "전체 하이라이트 목록 페이지로 이동합니다.", Toast.LENGTH_SHORT).show()
            }
        )



        rvHighlights?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvHighlights?.adapter = highlightAdapter

        // 4. 공개 다이어리 관찰
        viewModel.publicDiaries.observe(viewLifecycleOwner) { diaries ->
            if (diaries.isNullOrEmpty()) {
                Toast.makeText(context, "공개 중인 다이어리가 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val latestDiary = diaries.last()
                val targetDate = latestDiary.createdAt

                val detailFragment = DiaryDetailFragment.newInstance(targetDate, isEditMode = false)

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }


         */
        // 5. 버튼 클릭 이벤트
        ivBack.setOnClickListener { parentFragmentManager.popBackStack() }

        ivEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileEditFragment())
                .addToBackStack(null)
                .commit()
        }

        ivSearchFriend.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FriendListFragment())
                .addToBackStack(null)
                .commit()
        }

        tvFriendUpdateBadge.setOnClickListener {
            Toast.makeText(context, "새로 올라온 친구들의 다이어리를 띄웁니다!", Toast.LENGTH_SHORT).show()
        }

        // 연핑크회색 빈 카드 클릭 시 목록 새로고침
        cardEmptySharedFlashcard?.setOnClickListener {
            viewModel.fetchMySharedFolders()
            Toast.makeText(context, "공유 단어장 목록을 새로고침했습니다.", Toast.LENGTH_SHORT).show()
        }

        llSharedDiary.setOnClickListener {
            viewModel.fetchPublicDiaries()
        }
    }

    // 🌟 추가된 부분: 다른 화면에서 프로필 탭으로 돌아올 때마다 자동으로 최신 목록 재조회
    override fun onResume() {
        super.onResume()
        viewModel.fetchUserProfile()
        viewModel.fetchMySharedFolders()
    }

    // 🌟 FolderEntity 기반 공유 단어장 내부 어댑터
    private class SharedFlashcardAdapter(
        private val items: List<FolderEntity>,
        private val onItemClick: (FolderEntity) -> Unit
    ) : RecyclerView.Adapter<SharedFlashcardAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.item_shared_flashcard, parent, false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.folderName
            holder.tvCount.text = item.visibility // FolderEntity의 공개 상태 표시 (예: "전체공개")
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvFlashcardTitle)
            val tvCount: TextView = itemView.findViewById(R.id.tvFlashcardCount)
        }
    }
}