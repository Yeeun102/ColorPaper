package com.example.colorpaper.data.repository

import android.content.Context
import android.util.Log
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DiaryRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun saveDiariesToLocalAndRemote(diaries: List<DiaryEntity>): Boolean {
        return try {
            for (diary in diaries) {
                // 1. Room 로컬 DB 저장
                val savedId = db.diaryDao().insertPostIt(diary)
                val updatedDiary = diary.copy(diaryId = savedId.toInt())

                // 2. 🔥 Firebase Firestore 백엔드 서버 적재
                // 문서 키: {userId}_{createdAt}_{diaryId}
                val docRef = firestore.collection("diaries")
                    .document("${updatedDiary.userId}_${updatedDiary.createdAt}_$savedId")

                docRef.set(updatedDiary).await()
            }
            true
        } catch (e: Exception) {
            Log.e("DiaryRepository", "Firestore 동기화 중 오류 발생", e)
            false
        }
    }
}