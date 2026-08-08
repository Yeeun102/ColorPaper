package com.example.colorpaper.ui.friend

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.colorpaper.R
import com.example.colorpaper.databinding.ItemFriendSearchBinding

data class FriendUiModel(
    val userId: String, // Firebase UID
    val userCode: String,
    val nickname: String,
    val profileImageUrl: String?,
    var followerCount: Int = 0,
    var followingCount: Int = 0,
    var isFollowing: Boolean = false
)

class FriendSearchAdapter(
    private var friendList: List<FriendUiModel>,
    private val onFollowClick: (FriendUiModel, Int) -> Unit,
    private val onItemClick: (FriendUiModel) -> Unit
) : RecyclerView.Adapter<FriendSearchAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFriendSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendSearchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = friendList[position]

        holder.binding.apply {
            tvUserCode.text = "#${user.userCode}"
            tvNickname.text = user.nickname
            tvFollowerCount.text = "팔로워 ${user.followerCount}"
            tvFollowingCount.text = "팔로잉 ${user.followingCount}"

            ivProfile.load(user.profileImageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_default_profile)
                error(R.drawable.ic_default_profile)
                transformations(CircleCropTransformation())
            }

            // 💡 팔로우 상태에 따라 아이콘 변경 (+ <-> X)
            if (user.isFollowing) {
                btnFollow.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // 팔로우 취소 아이콘
            } else {
                btnFollow.setImageResource(android.R.drawable.ic_input_add) // 팔로우 (+) 아이콘
            }

            root.setOnClickListener { onItemClick(user) }
            btnFollow.setOnClickListener { onFollowClick(user, position) }
        }
    }

    override fun getItemCount(): Int = friendList.size

    fun updateList(newList: List<FriendUiModel>) {
        this.friendList = newList
        notifyDataSetChanged()
    }
}