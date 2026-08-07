package com.example.colorpaper.ui.diary

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentDiaryBinding

class DiaryFragment : Fragment(R.layout.fragment_diary) {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!
    private lateinit var pagerAdapter: DiaryPagerAdapter

    private var isMyPageMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isMyPageMode = arguments?.getBoolean(ARG_IS_MY_PAGE, false) ?: false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiaryBinding.bind(view)

        // ViewPager2 어댑터 연결
        pagerAdapter = DiaryPagerAdapter(this)
        binding.viewPagerDiary.adapter = pagerAdapter

        // 작성란(일반) 모드일 때는 좌우 스와이프 차단, 마이페이지 조회 모드일 때만 좌우 스와이프 허용
        binding.viewPagerDiary.isUserInputEnabled = isMyPageMode

        val initialDate = arguments?.getString(ARG_INITIAL_DATE)
        val targetPosition = pagerAdapter.getPositionForDate(initialDate)

        binding.viewPagerDiary.setCurrentItem(targetPosition, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL_DATE = "arg_initial_date"
        private const val ARG_IS_MY_PAGE = "arg_is_my_page"

        fun newInstance(date: String, isMyPage: Boolean = false): DiaryFragment {
            val fragment = DiaryFragment()
            val args = Bundle()
            args.putString(ARG_INITIAL_DATE, date)
            args.putBoolean(ARG_IS_MY_PAGE, isMyPage)
            fragment.arguments = args
            return fragment
        }
    }
}