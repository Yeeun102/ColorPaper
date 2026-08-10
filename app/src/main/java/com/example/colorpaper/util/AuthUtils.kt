package com.example.colorpaper.util

import com.google.firebase.auth.FirebaseAuth

object AuthUtils {
    // 현재 로그인한 유저의 UID를 반환 (비로그인 상태일 경우 빈 문자열)
    fun getCurrentUserId(): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        } catch (_: SecurityException) {
            ""
        }
    }
}