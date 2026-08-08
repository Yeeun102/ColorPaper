package com.example.colorpaper.ui.flashcard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.WordEntity
import com.example.colorpaper.data.repository.FlashcardRepository
import com.example.colorpaper.util.AuthUtils
import kotlinx.coroutines.launch

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FlashcardRepository

    private val _myFolders = MutableLiveData<List<FolderEntity>>()
    val myFolders: LiveData<List<FolderEntity>> get() = _myFolders

    private val _currentWords = MutableLiveData<List<WordEntity>>()
    val currentWords: LiveData<List<WordEntity>> get() = _currentWords

    init {
        val dao = AppDatabase.getDatabase(application).flashcardDao()
        repository = FlashcardRepository(dao)
    }

    // 내 단어장 목록 불러오기
    fun fetchMyFolders() {
        viewModelScope.launch {
            val userId = AuthUtils.getCurrentUserId()
            _myFolders.value = repository.getMyFolders(userId)
        }
    }

    // 특정 단어장 내부의 단어 목록 불러오기
    fun fetchWords(folderId: Int) {
        viewModelScope.launch {
            _currentWords.value = repository.getWordsByFolderId(folderId)
        }
    }

    // 새 단어장 추가
    fun createFolder(folderName: String, visibility: String = "전체공개") {
        viewModelScope.launch {
            val userId = AuthUtils.getCurrentUserId()
            val newFolder = FolderEntity(
                userId = userId,
                folderName = folderName,
                visibility = visibility
            )
            repository.createFolder(newFolder)
            fetchMyFolders() // 목록 갱신
        }
    }

    // 단어 목록 추가
    fun addWords(words: List<WordEntity>) {
        viewModelScope.launch {
            repository.insertWords(words)
            if (words.isNotEmpty()) {
                fetchWords(words.first().folderId)
            }
        }
    }

    // 단어 암기 상태 토글
    fun toggleWordMemorized(word: WordEntity) {
        viewModelScope.launch {
            val updatedWord = word.copy(isMemorized = !word.isMemorized)
            repository.updateWord(updatedWord)
            fetchWords(word.folderId) // 상태 반영 갱신
        }
    }

    // 단어장 삭제
    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            fetchMyFolders()
        }
    }
}