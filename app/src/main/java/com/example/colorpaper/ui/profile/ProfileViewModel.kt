package com.example.colorpaper.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.UserEntity
import com.example.colorpaper.data.repository.FlashcardRepository // 💡 추가
import com.example.colorpaper.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(db)
    private val flashcardRepository = FlashcardRepository(db.flashcardDao()) // 💡 FlashcardRepository 연결

    private val _userData = MutableLiveData<UserEntity?>()
    val userData: LiveData<UserEntity?> get() = _userData

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> get() = _saveResult

    private val _publicDiaries = MutableLiveData<List<DiaryEntity>>()
    val publicDiaries: LiveData<List<DiaryEntity>> get() = _publicDiaries

    private val _sharedFolders = MutableLiveData<List<FolderEntity>>()
    val sharedFolders: LiveData<List<FolderEntity>> get() = _sharedFolders

    fun fetchUserProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userRepository.getUserProfile()
            _userData.postValue(user)
        }
    }

    fun saveUserProfile(nickname: String, userCode: String, profileImageUriString: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = userRepository.updateUserProfile(nickname, userCode, profileImageUriString)
            _saveResult.postValue(success)
        }
    }

    fun fetchPublicDiaries() {
        viewModelScope.launch(Dispatchers.IO) {
            val diaries = userRepository.getPublicDiaries()
            _publicDiaries.postValue(diaries)
        }
    }

    fun fetchMySharedFolders(targetUserId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUser = userRepository.getUserProfile()
            val userId = targetUserId ?: currentUser?.userId?.toString() ?: userRepository.currentUid ?: ""

            // 💡 1. FlashcardRepository를 통해 해당 유저의 단어장 목록 조회
            val userFolders = flashcardRepository.getMyFolders(userId)

            // 💡 2. 전체 공개 단어장도 함께 불러오기 (필요시)
            val sharedFolders = flashcardRepository.getSharedFolders()

            // 💡 3. 유저 단어장 중 "비공개"가 아닌 것 + 전체공개 단어장 병합 (중복 제거)
            val resultFolders = (userFolders.filter { it.visibility != "비공개" } + sharedFolders)
                .distinctBy { it.folderId }

            _sharedFolders.postValue(resultFolders)
        }
    }
}