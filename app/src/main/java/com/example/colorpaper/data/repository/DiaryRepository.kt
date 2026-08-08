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

    // Firestore 및 Room에서 특정 유저의 해당 날짜 일기 가져오기
    suspend fun getDiariesByDateAndUser(date: String, userIdString: String): List<DiaryEntity> {
        return try {
            // 1. Firestore에서 데이터 우선 조회
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("userId", userIdString)
                .whereEqualTo("createdAt", date)
                .get()
                .await()

            val remoteList = snapshot.documents.mapNotNull { it.toObject(DiaryEntity::class.java) }
            if (remoteList.isNotEmpty()) {
                remoteList
            } else {
                // 2. Firestore에 없으면 로컬 Room DB 조회 (db.diaryDao() 사용 및 String 타입 전달)
                db.diaryDao().getPostItsByDateAndUserId(date, userIdString)
            }
        } catch (e: Exception) {
            // 실패 시 로컬 Room 백업 조회
            db.diaryDao().getPostItsByDateAndUserId(date, userIdString)
        }
    }

    suspend fun saveDiariesToLocalAndRemote(diaries: List<DiaryEntity>): Boolean {
        return try {
            for (diary in diaries) {
                // 1. Room 로컬 DB 저장
                val savedId = db.diaryDao().insertPostIt(diary)
                val updatedDiary = diary.copy(diaryId = savedId.toInt())

                // 2. Firebase Firestore 백엔드 서버 적재
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