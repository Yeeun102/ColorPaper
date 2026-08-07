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
import com.example.colorpaper.ui.diary.DiaryDetailFragment
import com.example.colorpaper.ui.flashcard.FlashcardStudyFragment
import com.example.colorpaper.ui.friend.FriendListFragment
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()
    private var profileFlashcardAdapter: ProfileFlashcardAdapter? = null

    // 💡 공유 다이어리 버튼 클릭 상태 플래그 (뒤로가기 시 무한 이동 방지)
    private var isSharedDiaryClicked = false

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

        val cardEmptySharedFlashcard = view.findViewById<MaterialCardView>(R.id.cardEmptySharedFlashcard)
        val rvSharedFlashcards = view.findViewById<RecyclerView>(R.id.rvSharedFlashcards)
        val rvHighlights = view.findViewById<RecyclerView>(R.id.rvHighlights)

        // 💡 프로필 전용 단어장 어댑터(ProfileFlashcardAdapter) 연동
        rvSharedFlashcards?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        profileFlashcardAdapter = ProfileFlashcardAdapter(
            setList = emptyList(),
            onStartClick = { folder ->
                // 단어장 클릭 시 FlashcardStudyFragment로 이동
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

        // 2. FolderEntity 데이터 관찰 및 ProfileFlashcardAdapter 갱신
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

        // 3. 하이라이트 세팅
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

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val highlightAdapter = HighlightAdapter(
            items = uniqueHighlights,
            onItemClick = { item ->
                val formattedTargetDate = try {
                    val parsedDate = displayFormat.parse(item.date)
                    val cal = Calendar.getInstance()
                    if (parsedDate != null) {
                        cal.time = parsedDate
                        cal.set(Calendar.YEAR, currentYear)
                        inputFormat.format(cal.time)
                    } else item.date
                } catch (e: Exception) {
                    item.date
                }

                navigateToDiaryDetail(formattedTargetDate)
            },
            onMoreClick = {
                Toast.makeText(context, "전체 하이라이트 목록 페이지로 이동합니다.", Toast.LENGTH_SHORT).show()
            }
        )

        rvHighlights?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvHighlights?.adapter = highlightAdapter

        // 4. 공개 다이어리 관찰 (클릭 이벤트 발생 시만 이동)
        viewModel.publicDiaries.observe(viewLifecycleOwner) { diaries ->
            if (isSharedDiaryClicked) {
                isSharedDiaryClicked = false
                if (diaries.isNullOrEmpty()) {
                    Toast.makeText(context, "공개 중인 다이어리가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val latestDiary = diaries.last()
                    navigateToDiaryDetail(latestDiary.createdAt)
                }
            }
        }

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

        cardEmptySharedFlashcard?.setOnClickListener {
            val targetUserId = arguments?.getString("TARGET_USER_ID")
            viewModel.fetchMySharedFolders(targetUserId)
            Toast.makeText(context, "공유 단어장 목록을 새로고침했습니다.", Toast.LENGTH_SHORT).show()
        }

        llSharedDiary.setOnClickListener {
            isSharedDiaryClicked = true
            viewModel.fetchPublicDiaries()
        }
    }

    private fun navigateToDiaryDetail(targetDate: String) {
        val detailFragment = DiaryDetailFragment().apply {
            arguments = Bundle().apply {
                putString("TARGET_DATE", targetDate)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchUserProfile()
        val targetUserId = arguments?.getString("TARGET_USER_ID")
        viewModel.fetchMySharedFolders(targetUserId)
    }
}