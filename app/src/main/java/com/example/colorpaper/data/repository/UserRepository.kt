package com.example.colorpaper.data.repository

import android.net.Uri
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.HighlightEntity
import com.example.colorpaper.data.model.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository(
    private val db: AppDatabase,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String get() = auth.currentUser?.email ?: "default@email.com"

    // 1-1. 내 프로필 정보 조회
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

    // 1-2. 타 유저 프로필 조회 (UID 및 userCode 검색 대응)
    suspend fun getUserById(targetUserId: String): UserEntity? {
        try {
            // 1) UID 문서로 직접 조회
            val doc = firestore.collection("users").document(targetUserId).get().await()
            if (doc.exists()) {
                val nickname = doc.getString("nickname") ?: ""
                val userCode = doc.getString("userCode") ?: ""
                val profileImageUrl = doc.getString("profileImageUrl")

                return UserEntity(
                    userId = targetUserId.hashCode(),
                    userCode = userCode,
                    email = doc.id, // Firestore UID 보존
                    passwordHash = "",
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                    membershipStatus = "FREE",
                    pushEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
            }

            // 2) userCode 검색 조건으로 조회
            val querySnap = firestore.collection("users")
                .whereEqualTo("userCode", targetUserId)
                .get()
                .await()

            if (!querySnap.isEmpty) {
                val userDoc = querySnap.documents[0]
                val nickname = userDoc.getString("nickname") ?: ""
                val userCode = userDoc.getString("userCode") ?: ""
                val profileImageUrl = userDoc.getString("profileImageUrl")

                return UserEntity(
                    userId = userDoc.id.hashCode(),
                    userCode = userCode,
                    email = userDoc.id, // Firestore UID 보존
                    passwordHash = "",
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                    membershipStatus = "FREE",
                    pushEnabled = true,
                    createdAt = System.currentTimeMillis()
                )
            }
        } catch (_: Exception) { }

        val numericId = targetUserId.toIntOrNull() ?: targetUserId.hashCode()
        return db.userDao().getUserById(numericId)
    }

    // 2. 프로필 정보 저장
    suspend fun updateUserProfile(
        nickname: String,
        userCode: String,
        profileImageUriString: String?
    ): Boolean {
        val uid = currentUid ?: run {
            android.util.Log.e("UserRepository", "현재 로그인된 UID가 없어 저장을 취소합니다.")
            return false
        }
        var downloadUrl: String? = null

        try {
            if (!profileImageUriString.isNullOrEmpty()) {
                val uri = Uri.parse(profileImageUriString)

                // http나 https로 시작하는 이미 존재하는 웹 URL이 아닌 경우 (로컬 선택/촬영 이미지)
                if (!profileImageUriString.startsWith("http://") && !profileImageUriString.startsWith("https://")) {
                    android.util.Log.d("UserRepository", "Firebase Storage 이미지 업로드 시도: $uri")

                    val storageRef = storage.reference.child("profile_images/$uid.jpg")

                    // Storage에 파일 업로드
                    storageRef.putFile(uri).await()

                    // 다운로드 가능 URL 획득
                    downloadUrl = storageRef.downloadUrl.await().toString()
                    android.util.Log.d("UserRepository", "Storage 업로드 완료 URL: $downloadUrl")
                } else {
                    // 이미 업로드된 http(s) URL인 경우 그대로 사용
                    downloadUrl = profileImageUriString
                }
            }

            val userMap = hashMapOf<String, Any>(
                "nickname" to nickname,
                "userCode" to userCode,
                "updatedAt" to System.currentTimeMillis()
            )
            if (!downloadUrl.isNullOrEmpty()) {
                userMap["profileImageUrl"] = downloadUrl
            }

            // Firestore 사용자 정보 업데이트 (merge 옵션)
            firestore.collection("users").document(uid)
                .set(userMap, SetOptions.merge())
                .await()

            // Room DB 로컬 데이터도 최신화
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
            // ★ 로그를 남겨 정확히 무슨 에러로 실패했는지 확인 가능하게 변경
            android.util.Log.e("UserRepository", "updateUserProfile 실패 원인: ${e.message}", e)
            return false
        }
    }

    // 3-1. 전체 공개 다이어리 전체 조회
    suspend fun getPublicDiaries(): List<DiaryEntity> {
        return try {
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("visibility", "전체공개")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try { doc.toObject(DiaryEntity::class.java) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            db.diaryDao().getPublicDiaries("전체공개")
        }
    }

    // 3-2. 특정 유저 ID 기준 공개 다이어리 조회 (관계별 공개 범위 반영)
    suspend fun getPublicDiariesByUserId(targetUserId: String): List<DiaryEntity> {
        val myUid = currentUid ?: ""
        val isMe = (myUid == targetUserId)
        val isFollowing = if (!isMe && myUid.isNotEmpty()) checkIsFollowing(myUid, targetUserId) else false

        val allowedVisibilities = when {
            isMe -> listOf("전체공개", "팔로워공개", "비공개")
            isFollowing -> listOf("전체공개", "팔로워공개")
            else -> listOf("전체공개")
        }

        return try {
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("userId", targetUserId)
                .whereIn("visibility", allowedVisibilities)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try { doc.toObject(DiaryEntity::class.java) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 4. 공유 단어장 조회
    suspend fun getSharedFolders(userId: String): List<FolderEntity> {
        return getFoldersByRelationship(userId)
    }

    // 5. 관계별 단어장 조회
    suspend fun getFoldersByRelationship(targetUserId: String): List<FolderEntity> {
        val myUid = currentUid ?: ""
        val isMe = (myUid == targetUserId)
        val isFollowing = if (!isMe && myUid.isNotEmpty()) checkIsFollowing(myUid, targetUserId) else false

        val allowedVisibilities = when {
            isMe -> listOf("전체공개", "팔로워공개", "비공개")
            isFollowing -> listOf("전체공개", "팔로워공개")
            else -> listOf("전체공개")
        }

        return try {
            val snapshot = firestore.collection("folders")
                .whereEqualTo("userId", targetUserId)
                .whereIn("visibility", allowedVisibilities)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val folderName = doc.getString("folderName") ?: doc.getString("folder_name") ?: ""
                val visibility = doc.getString("visibility") ?: "전체공개"
                val isAuto = doc.getBoolean("isAutoGenerated") ?: false
                val rawFolderId = doc.getLong("folderId")?.toInt() ?: doc.id.hashCode()

                FolderEntity(
                    folderId = rawFolderId,
                    userId = targetUserId,
                    folderName = folderName,
                    isAutoGenerated = isAuto,
                    visibility = visibility
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 6. 팔로우 여부 확인
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

    // 8. 특정 유저의 하이라이트 목록 조회
    // 하이라이트 전용 컬렉션 대신, 실제 다이어리의 isHighlighted 상태를 단일 소스로 사용한다.
    suspend fun getHighlightsByUserId(targetUserId: String): List<HighlightEntity> {
        val resolvedUid = resolveUidOrNull(targetUserId) ?: targetUserId

        return try {
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("userId", resolvedUid)
                .whereEqualTo("isHighlighted", true)
                .get()
                .await()

            val remoteItems = snapshot.documents.mapNotNull { doc ->
                val diary = doc.toObject(DiaryEntity::class.java) ?: return@mapNotNull null
                if (diary.content.startsWith("[DECO]:")) return@mapNotNull null

                HighlightEntity(
                    highlightId = if (diary.diaryId != 0) diary.diaryId else doc.id.hashCode(),
                    diaryId = diary.diaryId,
                    date = diary.createdAt,
                    highlightedText = diary.content
                )
            }.sortedByDescending { it.date }

            if (remoteItems.isNotEmpty()) {
                remoteItems
            } else {
                db.diaryDao().getDiariesByUserId(resolvedUid)
                    .asSequence()
                    .filter { it.isHighlighted && !it.content.startsWith("[DECO]:") }
                    .sortedByDescending { it.createdAt }
                    .map { diary ->
                        HighlightEntity(
                            highlightId = diary.diaryId,
                            diaryId = diary.diaryId,
                            date = diary.createdAt,
                            highlightedText = diary.content
                        )
                    }
                    .toList()
            }
        } catch (_: Exception) {
            // 네트워크 에러 시 로컬 DB에서 동일 조건으로 조회
            db.diaryDao().getDiariesByUserId(resolvedUid)
                .asSequence()
                .filter { it.isHighlighted && !it.content.startsWith("[DECO]:") }
                .sortedByDescending { it.createdAt }
                .map { diary ->
                    HighlightEntity(
                        highlightId = diary.diaryId,
                        diaryId = diary.diaryId,
                        date = diary.createdAt,
                        highlightedText = diary.content
                    )
                }
                .toList()
        }
    }

    private suspend fun resolveUidOrNull(targetUserId: String): String? {
        return try {
            val directDoc = firestore.collection("users").document(targetUserId).get().await()
            if (directDoc.exists()) {
                targetUserId
            } else {
                val querySnap = firestore.collection("users")
                    .whereEqualTo("userCode", targetUserId)
                    .limit(1)
                    .get()
                    .await()

                querySnap.documents.firstOrNull()?.id
            }
        } catch (_: Exception) {
            null
        }
    }

    // 7. 유저 실시간 검색 (userCode 및 nickname 검색 지원)
    suspend fun searchUsers(query: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val cleanQuery = query.trim().removePrefix("#")

        try {
            // 1) userCode 기준 범위 검색
            val snapshotByCode = firestore.collection("users")
                .orderBy("userCode")
                .startAt(cleanQuery)
                .endAt(cleanQuery + "\uf8ff")
                .get()
                .await()

            val results = mutableListOf<UserEntity>()
            val addedUids = mutableSetOf<String>()

            for (doc in snapshotByCode.documents) {
                if (doc.id == currentUid) continue // 본인은 검색 제외
                val userCode = doc.getString("userCode") ?: ""
                val nickname = doc.getString("nickname") ?: ""
                val profileImageUrl = doc.getString("profileImageUrl")

                addedUids.add(doc.id)
                results.add(
                    UserEntity(
                        userId = doc.id.hashCode(),
                        userCode = userCode,
                        email = doc.id, // ★ email 필드에 실제 Firestore Document UID 전달
                        passwordHash = "",
                        nickname = nickname,
                        profileImageUrl = profileImageUrl
                    )
                )
            }

            // 2) nickname 기준 추가 검색 (결과 보완)
            val snapshotByNick = firestore.collection("users")
                .orderBy("nickname")
                .startAt(cleanQuery)
                .endAt(cleanQuery + "\uf8ff")
                .get()
                .await()

            for (doc in snapshotByNick.documents) {
                if (doc.id == currentUid || addedUids.contains(doc.id)) continue
                val userCode = doc.getString("userCode") ?: ""
                val nickname = doc.getString("nickname") ?: ""
                val profileImageUrl = doc.getString("profileImageUrl")

                results.add(
                    UserEntity(
                        userId = doc.id.hashCode(),
                        userCode = userCode,
                        email = doc.id, // ★ email 필드에 실제 Firestore Document UID 전달
                        passwordHash = "",
                        nickname = nickname,
                        profileImageUrl = profileImageUrl
                    )
                )
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }
}