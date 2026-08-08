package com.example.colorpaper.ui.diary

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.repository.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DiaryRepository(application)

    // 저장 성공 여부를 UI(Fragment)로 전달하기 위한 LiveData
    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> get() = _saveSuccess

    /**
     * 다이어리 목록(포스트잇, 텍스트)을 로컬 Room DB 및 Firebase Firestore에 함께 저장
     */
    fun saveDiaries(diaries: List<DiaryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isSuccess = repository.saveDiariesToLocalAndRemote(diaries)
                _saveSuccess.postValue(isSuccess)
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "다이어리 저장 중 예외 발생", e)
                _saveSuccess.postValue(false)
            }
        }
    }
}