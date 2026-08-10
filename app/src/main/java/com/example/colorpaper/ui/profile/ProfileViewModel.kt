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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(db)
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

                // repository를 통해 Firestore → Room DB 순으로 조회
                val user = if (targetUserId.isNullOrEmpty()) {
                    userRepository.getUserProfile()
                } else {
                    userRepository.getUserById(uid)
                }
                _userData.postValue(user)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "프로필 로드 중 오류 발생: ${e.message}", e)
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

                // getHighlightsByUserId 내부에서 resolveUidOrNull로 UID → userCode fallback 처리하므로
                // 한 번만 호출하면 충분함
                val entities = userRepository.getHighlightsByUserId(targetId)

                val dedupedByDate = entities
                    .sortedByDescending { it.date }
                    .distinctBy { it.date }

                val uiItems = dedupedByDate.map { entity ->
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