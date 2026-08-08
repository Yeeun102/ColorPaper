package com.example.colorpaper.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.UserEntity
import com.example.colorpaper.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(db)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _userData = MutableLiveData<UserEntity?>()
    val userData: LiveData<UserEntity?> get() = _userData

    private val _sharedFolders = MutableLiveData<List<FolderEntity>>()
    val sharedFolders: LiveData<List<FolderEntity>> get() = _sharedFolders

    private val _publicDiaries = MutableLiveData<List<DiaryEntity>>()
    val publicDiaries: LiveData<List<DiaryEntity>> get() = _publicDiaries

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> get() = _saveResult

    // 🌟 1. 유저 프로필 조회 (Firebase Auth 및 Firestore 기반으로 변경)
    fun fetchUserProfile(targetUserId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // targetUserId가 없으면 현재 로그인한 Firebase UID 사용
                val uid = if (targetUserId.isNullOrEmpty()) {
                    auth.currentUser?.uid ?: ""
                } else {
                    targetUserId
                }

                if (uid.isBlank()) {
                    Log.e("ProfileViewModel", "현재 로그인된 UID가 존재하지 않습니다.")
                    _userData.postValue(null)
                    return@launch
                }

                // 1순위: Firestore 'users' 컬렉션에서 사용자 정보 가져오기
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val nickname = doc.getString("nickname") ?: "알 수 없음"
                    val userCode = doc.getString("userCode") ?: ""
                    val profileImageUrl = doc.getString("profileImageUrl")
                    val email = doc.getString("email") ?: (auth.currentUser?.email ?: "")

                    val fetchedUser = UserEntity(
                        userCode = userCode,
                        email = email,
                        passwordHash = "",
                        nickname = nickname,
                        profileImageUrl = profileImageUrl
                    )
                    _userData.postValue(fetchedUser)
                } else {
                    // Firestore에 데이터가 없으면 Room DB 백업 조회
                    Log.w("ProfileViewModel", "Firestore 문서 없음. 로컬 DB 조회를 시도합니다.")
                    val localUser = userRepository.getUserProfile()
                    _userData.postValue(localUser)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "프로필 로드 중 오류 발생: ${e.message}", e)
                // 네트워크 에러 등으로 실패 시 로컬 DB 시도
                try {
                    val localUser = userRepository.getUserProfile()
                    _userData.postValue(localUser)
                } catch (dbEx: Exception) {
                    _userData.postValue(null)
                }
            }
        }
    }

    // 🌟 2. 공유 단어장 조회 (안전한 UID 보장)
    fun fetchMySharedFolders(targetUserId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentAuthUid = auth.currentUser?.uid ?: ""
                val targetId = if (!targetUserId.isNullOrEmpty()) targetUserId else currentAuthUid

                if (targetId.isBlank()) {
                    _sharedFolders.postValue(emptyList())
                    return@launch
                }

                val folders = userRepository.getFoldersByRelationship(targetId)
                _sharedFolders.postValue(folders ?: emptyList())
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "단어장 로드 실패: ${e.message}")
                _sharedFolders.postValue(emptyList())
            }
        }
    }

    // 🌟 3. 공개 다이어리 조회
    fun fetchPublicDiaries(targetUserId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentAuthUid = auth.currentUser?.uid ?: ""
                val targetId = if (!targetUserId.isNullOrEmpty()) targetUserId else currentAuthUid

                val diaries = if (targetId.isBlank()) {
                    emptyList()
                } else {
                    userRepository.getPublicDiariesByUserId(targetId)
                }
                _publicDiaries.postValue(diaries ?: emptyList())
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "공개 다이어리 로드 실패: ${e.message}")
                _publicDiaries.postValue(emptyList())
            }
        }
    }

    // 4. 프로필 정보 수정 저장
    fun saveUserProfile(nickname: String, userCode: String, profileImageUrl: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isSuccess = userRepository.updateUserProfile(nickname, userCode, profileImageUrl)
                _saveResult.postValue(isSuccess)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "프로필 수정 실패: ${e.message}")
                _saveResult.postValue(false)
            }
        }
    }

    private val _highlights = MutableLiveData<List<HighlightItem>>()
    val highlights: LiveData<List<HighlightItem>> get() = _highlights

    fun fetchHighlights(targetUserId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentAuthUid = auth.currentUser?.uid ?: ""
                val targetId = if (!targetUserId.isNullOrEmpty()) targetUserId else currentAuthUid

                if (targetId.isBlank()) {
                    _highlights.postValue(emptyList())
                    return@launch
                }

                // 1. Room DB 1차 조회 (Firebase Auth UID 기준)
                var entities = userRepository.getHighlightsByUserId(targetId)

                // 2. 만약 UID로 안 잡힐 경우, 현재 유저의 userCode로 2차 조회 시도
                if (entities.isNullOrEmpty()) {
                    val userDoc = firestore.collection("users").document(targetId).get().await()
                    val userCode = userDoc.getString("userCode")
                    if (!userCode.isNullOrEmpty()) {
                        entities = userRepository.getHighlightsByUserId(userCode)
                    }
                }

                val uiItems = (entities ?: emptyList()).map { entity ->
                    HighlightItem(
                        id = entity.highlightId.toString(),
                        diaryId = entity.diaryId,
                        date = entity.date,
                        highlightedText = entity.highlightedText
                    )
                }
                _highlights.postValue(uiItems)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "하이라이트 로드 실패: ${e.message}")
                _highlights.postValue(emptyList())
            }
        }
    }
}