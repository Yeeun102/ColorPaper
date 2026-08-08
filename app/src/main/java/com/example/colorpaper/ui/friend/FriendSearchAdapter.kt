package com.example.colorpaper.ui.friend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.model.UserEntity

class FriendSearchAdapter(
    private var friendList: List<UserEntity>,
    private val onFollowClick: (UserEntity) -> Unit,
    private val onItemClick: (UserEntity) -> Unit
) : RecyclerView.Adapter<FriendSearchAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
        val tvNickname: TextView = itemView.findViewById(R.id.tvNickname)
        val tvUserCode: TextView = itemView.findViewById(R.id.tvUserCode)
        val btnFollow: Button = itemView.findViewById(R.id.btnFollow)

        fun bind(user: UserEntity) {
            tvNickname.text = user.nickname
            tvUserCode.text = "@${user.userCode}"

            // 팔로우 버튼 클릭 시
            btnFollow.setOnClickListener {
                onFollowClick(user)
            }

            // 항목 전체 클릭 시 (친구 프로필 홈으로 이동용)
            itemView.setOnClickListener {
                onItemClick(user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_search, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(friendList[position])
    }

    override fun getItemCount(): Int = friendList.size

    fun updateList(newList: List<UserEntity>) {
        friendList = newList
        notifyDataSetChanged()
    }
}