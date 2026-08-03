package com.example.colorpaper.ui.friend

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import kotlinx.coroutines.launch

class FriendProfileFragment : Fragment() {

    private var targetUserId: Int = -1 // 조회할 친구 user_id

    companion object {
        private const val ARG_USER_ID = "target_user_id"

        fun newInstance(userId: Int): FriendProfileFragment {
            val fragment = FriendProfileFragment()
            val args = Bundle()
            args.putInt(ARG_USER_ID, userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            targetUserId = it.getInt(ARG_USER_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_friend_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        val tvUserCodeTop = view.findViewById<TextView>(R.id.tvUserCodeTop)
        val tvNickname = view.findViewById<TextView>(R.id.tvNickname)
        val btnCopyCode = view.findViewById<Button>(R.id.btnCopyCode)
        val llFriendDiary = view.findViewById<LinearLayout>(R.id.llFriendDiary)

        val db = AppDatabase.getDatabase(requireContext())

        // 1. 뒤로가기
        ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. 전달받은 친구 유저 정보 DB에서 긁어오기
        if (targetUserId != -1) {
            viewLifecycleOwner.lifecycleScope.launch {
                val friendInfo = db.userDao().getUserById(targetUserId)
                if (friendInfo != null) {
                    tvNickname.text = friendInfo.nickname
                    tvUserCodeTop.text = "#${friendInfo.userCode}"

                    // 코드 복사 버튼 기능
                    btnCopyCode.setOnClickListener {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("UserCode", friendInfo.userCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "유저코드가 복사되었습니다!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 3. 다이어리 커버 탭 시 ➔ 7.1 친구 다이어리 속지 화면으로 이동!
        llFriendDiary.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FriendDiaryFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}