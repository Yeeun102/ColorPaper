package com.example.colorpaper.data.repository

import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: AppDatabase,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String get() = auth.currentUser?.email ?: "default@email.com"

    // 프로필 정보 조회 (Firebase 우선 -> 실패 시 Local Room DB)
    suspend fun getUserProfile(): UserEntity? {
        val uid = currentUid
        if (uid != null) {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val nickname = doc.getString("nickname") ?: ""
                    val userCode = doc.getString("userCode") ?: ""
                    // 🌟 Firestore에서 profileImageUrl 가져오기
                    val profileImageUrl = doc.getString("profileImageUrl")

                    return UserEntity(
                        userId = 1,
                        userCode = userCode,
                        email = currentEmail,
                        passwordHash = "",
                        nickname = nickname,
                        profileImageUrl = profileImageUrl, // 🌟 저장된 이미지 URL 반영
                        membershipStatus = "FREE",
                        pushEnabled = true,
                        createdAt = System.currentTimeMillis()
                    )
                }
            } catch (_: Exception) { }
        }
        return db.userDao().getUserById(1)
    }

    // 프로필 정보 저장 (Firebase & Local Room DB 동시 업데이트)
    // 🌟 profileImageUrl 파라미터 추가 (기본값 null)
    suspend fun updateUserProfile(
        nickname: String,
        userCode: String,
        profileImageUrl: String? = null
    ): Boolean {
        val uid = currentUid
        val userMap = hashMapOf<String, Any>(
            "nickname" to nickname,
            "userCode" to userCode,
            "updatedAt" to System.currentTimeMillis()
        )

        // 🌟 프로필 이미지 URL이 새로 들어온 경우 Firestore 전송 객체에 포함
        if (!profileImageUrl.isNullOrEmpty()) {
            userMap["profileImageUrl"] = profileImageUrl
        }

        if (uid != null) {
            try {
                firestore.collection("users").document(uid)
                    .set(userMap, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                return false
            }
        }

        // Room DB도 업데이트 (새 이미지가 설정되었으면 새 이미지로, 아니면 기존 이미지 유지)
        val existingUser = db.userDao().getUserById(1)
        val updatedUser = UserEntity(
            userId = 1,
            userCode = userCode,
            email = currentEmail,
            passwordHash = existingUser?.passwordHash ?: "",
            nickname = nickname,
            profileImageUrl = profileImageUrl ?: existingUser?.profileImageUrl, // 🌟 이미지 경로 최신화
            membershipStatus = existingUser?.membershipStatus ?: "FREE",
            pushEnabled = existingUser?.pushEnabled ?: true,
            createdAt = existingUser?.createdAt ?: System.currentTimeMillis()
        )
        db.userDao().insertUser(updatedUser)
        return true
    }

    suspend fun getPublicDiaries(): List<DiaryEntity> {
        return db.diaryDao().getPublicDiaries("전체공개")
    }
}