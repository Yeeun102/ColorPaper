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
import com.example.colorpaper.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = UserRepository(db)

    private val _userData = MutableLiveData<UserEntity?>()
    val userData: LiveData<UserEntity?> get() = _userData

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> get() = _saveResult

    private val _publicDiaries = MutableLiveData<List<DiaryEntity>>()
    val publicDiaries: LiveData<List<DiaryEntity>> get() = _publicDiaries

    // 🌟 공유 단어장 세트 목록 LiveData 추가
    private val _sharedFolders = MutableLiveData<List<FolderEntity>>()
    val sharedFolders: LiveData<List<FolderEntity>> get() = _sharedFolders

    fun fetchUserProfile() {
        viewModelScope.launch {
            val user = repository.getUserProfile()
            _userData.value = user
        }
    }

    // 🌟 profileImageUrl 파라미터 추가 (기본값 null)
    fun saveUserProfile(nickname: String, userCode: String, profileImageUrl: String? = null) {
        viewModelScope.launch {
            val success = repository.updateUserProfile(nickname, userCode, profileImageUrl)
            _saveResult.value = success
        }
    }

    fun fetchPublicDiaries() {
        viewModelScope.launch {
            val diaries = repository.getPublicDiaries()
            _publicDiaries.value = diaries
        }
    }

    fun fetchMySharedFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUser = repository.getUserProfile()
            // .toString()을 명시하여 userId를 항상 String 타입으로 전달합니다.
            val userId = currentUser?.userId?.toString() ?: ""

            // DAO를 통한 전체공개 단어장 조회
            val folders = db.flashcardDao().getFlashcardSetsByVisibility(userId, "전체공개")
            _sharedFolders.postValue(folders)
        }
    }
}