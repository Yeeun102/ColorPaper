package com.example.colorpaper.ui.diary

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
@RequiresApi(Build.VERSION_CODES.O)
class DiaryPagerAdapter(
    fragment: Fragment,
    private val startDate: LocalDate = LocalDate.now().minusYears(1),
    private val isEditMode: Boolean = true // 기본값: 작성/수정 모드 (사진 2)
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 731


    override fun createFragment(position: Int): Fragment {
        val targetDate = startDate.plusDays(position.toLong())
        // 전달받은 isEditMode 상태를 그대로 DiaryDetailFragment에 넘겨줍니다.
        return DiaryDetailFragment.newInstance(targetDate.toString(), isEditMode = isEditMode)
    }



    fun getTodayPosition(): Int {
        return getPositionForDate(LocalDate.now().toString())
    }

    fun getPositionForDate(dateStr: String?): Int {
        if (dateStr.isNullOrEmpty()) {
            return getTodayPosition()
        }
        return try {
            val targetDate = LocalDate.parse(dateStr)
            ChronoUnit.DAYS.between(startDate, targetDate).toInt()
        } catch (e: Exception) {
            getTodayPosition()
        }
    }
}

 */
