package com.example.colorpaper.data.repository

import android.net.Uri
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: AppDatabase,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String get() = auth.currentUser?.email ?: "default@email.com"

    // 1. 프로필 정보 조회 (Firebase 우선 -> 실패 시 Local Room DB)
    suspend fun getUserProfile(): UserEntity? {
        val uid = currentUid
        if (uid != null) {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val nickname = doc.getString("nickname") ?: ""
                    val userCode = doc.getString("userCode") ?: ""
                    val profileImageUrl = doc.getString("profileImageUrl")

                    return UserEntity(
                        userId = 1,
                        userCode = userCode,
                        email = currentEmail,
                        passwordHash = "",
                        nickname = nickname,
                        profileImageUrl = profileImageUrl,
                        membershipStatus = "FREE",
                        pushEnabled = true,
                        createdAt = System.currentTimeMillis()
                    )
                }
            } catch (_: Exception) { }
        }
        return db.userDao().getUserById(1)
    }

    // 2. 프로필 정보 저장 (이미지 Storage 업로드 포함)
    suspend fun updateUserProfile(
        nickname: String,
        userCode: String,
        profileImageUriString: String?
    ): Boolean {
        val uid = currentUid ?: return false
        var downloadUrl: String? = null

        try {
            // 만약 새 이미지를 골랐다면 Firebase Storage에 업로드 후 다운로드 URL 획득
            if (!profileImageUriString.isNullOrEmpty() && profileImageUriString.startsWith("content://")) {
                val uri = Uri.parse(profileImageUriString)
                val storageRef = storage.reference.child("profile_images/$uid.jpg")
                storageRef.putFile(uri).await()
                downloadUrl = storageRef.downloadUrl.await().toString()
            } else {
                downloadUrl = profileImageUriString // 기존 URL 유지
            }

            val userMap = hashMapOf<String, Any>(
                "nickname" to nickname,
                "userCode" to userCode,
                "updatedAt" to System.currentTimeMillis()
            )
            if (!downloadUrl.isNullOrEmpty()) {
                userMap["profileImageUrl"] = downloadUrl
            }

            // Firestore 업로드
            firestore.collection("users").document(uid)
                .set(userMap, SetOptions.merge())
                .await()

            // Local Room DB 동기화
            val existingUser = db.userDao().getUserById(1)
            val updatedUser = UserEntity(
                userId = 1,
                userCode = userCode,
                email = currentEmail,
                passwordHash = existingUser?.passwordHash ?: "",
                nickname = nickname,
                profileImageUrl = downloadUrl ?: existingUser?.profileImageUrl,
                membershipStatus = existingUser?.membershipStatus ?: "FREE",
                pushEnabled = existingUser?.pushEnabled ?: true,
                createdAt = existingUser?.createdAt ?: System.currentTimeMillis()
            )
            db.userDao().insertUser(updatedUser)
            return true

        } catch (e: Exception) {
            return false
        }
    }

    // 3. 전체 공개 다이어리 조회 (Firebase 우선 -> 실패 시 Room)
    suspend fun getPublicDiaries(): List<DiaryEntity> {
        return try {
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("visibility", "전체공개")
                .get()
                .await()

            val remoteDiaries = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(DiaryEntity::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            if (remoteDiaries.isNotEmpty()) {
                remoteDiaries
            } else {
                db.diaryDao().getPublicDiaries("전체공개")
            }
        } catch (e: Exception) {
            db.diaryDao().getPublicDiaries("전체공개")
        }
    }

    // 4. 공유 단어장 조회 (Firebase 우선 -> 실패 시 Room)
    suspend fun getSharedFolders(userId: String): List<FolderEntity> {
        val firebaseUid = currentUid ?: userId

        return try {
            val snapshot = firestore.collection("folders")
                .whereEqualTo("visibility", "전체공개")
                .get()
                .await()

            // Firestore 문서를 FolderEntity로 안전하게 수동 변환
            val remoteFolders = snapshot.documents.mapNotNull { doc ->
                val folderName = doc.getString("folderName") ?: doc.getString("folder_name") ?: ""
                val visibility = doc.getString("visibility") ?: "전체공개"
                val isAuto = doc.getBoolean("isAutoGenerated") ?: doc.getBoolean("is_auto_generated") ?: false
                val rawFolderId = doc.getLong("folderId")?.toInt() ?: doc.id.hashCode()
                val docUserId = doc.getString("userId") ?: firebaseUid

                FolderEntity(
                    folderId = rawFolderId,
                    userId = docUserId,
                    folderName = folderName,
                    isAutoGenerated = isAuto,
                    visibility = visibility
                )
            }

            if (remoteFolders.isNotEmpty()) {
                remoteFolders
            } else {
                db.flashcardDao().getSharedFlashcardSets()
            }
        } catch (e: Exception) {
            db.flashcardDao().getSharedFlashcardSets()
        }
    }

    // UserRepository.kt 내부에 추가/수정

    suspend fun getFoldersByRelationship(targetUserId: String): List<FolderEntity> {
        val myUid = currentUid ?: ""

        // 1. 관계 확인
        val isMe = (myUid == targetUserId)
        val isFollowing = if (!isMe && myUid.isNotEmpty()) {
            checkIsFollowing(myUid, targetUserId)
        } else false

        // 2. 허용할 공개범위 목록 정의
        val allowedVisibilities = when {
            isMe -> listOf("전체공개", "팔로워공개", "비공개")
            isFollowing -> listOf("전체공개", "팔로워공개")
            else -> listOf("전체공개")
        }

        return try {
            // Firestore 필터링 조회
            val snapshot = firestore.collection("folders")
                .whereEqualTo("userId", targetUserId)
                .whereIn("visibility", allowedVisibilities)
                .get()
                .await()

            val remoteFolders = snapshot.documents.mapNotNull { doc ->
                val folderName = doc.getString("folderName") ?: doc.getString("folder_name") ?: ""
                val visibility = doc.getString("visibility") ?: "전체공개"
                val isAuto = doc.getBoolean("isAutoGenerated") ?: doc.getBoolean("is_auto_generated") ?: false
                val rawFolderId = doc.getLong("folderId")?.toInt() ?: doc.id.hashCode()

                FolderEntity(
                    folderId = rawFolderId,
                    userId = targetUserId,
                    folderName = folderName,
                    isAutoGenerated = isAuto,
                    visibility = visibility
                )
            }

            if (remoteFolders.isNotEmpty()) remoteFolders
            else db.flashcardDao().getFlashcardSetsByVisibility(targetUserId, "전체공개")
        } catch (e: Exception) {
            db.flashcardDao().getSharedFlashcardSets()
        }
    }

    // 팔로우 여부 확인 헬퍼 함수
    private suspend fun checkIsFollowing(myUid: String, targetUserId: String): Boolean {
        return try {
            val doc = firestore.collection("users")
                .document(myUid)
                .collection("following")
                .document(targetUserId)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

}