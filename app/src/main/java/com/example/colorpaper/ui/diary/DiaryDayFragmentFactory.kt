package com.example.colorpaper.ui.diary

import android.os.Bundle
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiaryDayFragmentFactory {
    fun create(dateKey: String): Fragment {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        return if (dateKey == today) {
            DiaryFragment()
        } else {
            DiaryDetailFragment().apply {
                arguments = Bundle().apply { putString("TARGET_DATE", dateKey) }
            }
        }
    }
}
