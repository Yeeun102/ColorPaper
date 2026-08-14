package com.example.colorpaper.data.repository

import android.content.Context
import android.util.Log
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.reminder.ReminderSchedulePolicy
import com.example.colorpaper.reminder.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DiaryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getDatabase(appContext)
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

    suspend fun syncUserDiariesFromRemote(userId: String): Int {
        val remoteDiaries = firestore.collection("diaries")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(DiaryEntity::class.java) }

        val dao = db.diaryDao()
        val localDiaries = dao.getDiariesByUserId(userId)
        val localKeys = localDiaries.mapTo(mutableSetOf()) { it.syncKey() }
        var restoredCount = 0

        remoteDiaries.forEach { remote ->
            val existingById = remote.diaryId.takeIf { it > 0 }?.let { dao.getDiaryById(it) }
            if (existingById?.userId == userId || remote.syncKey() in localKeys) return@forEach

            val diaryToInsert = if (existingById == null) remote else remote.copy(diaryId = 0)
            val savedId = dao.insertPostIt(diaryToInsert).toInt()
            val restoredDiary = diaryToInsert.copy(diaryId = savedId)
            localKeys += restoredDiary.syncKey()
            if (restoredDiary.reviewCycleDays != ReminderSchedulePolicy.DISABLED) {
                ReminderScheduler.schedule(appContext, restoredDiary)
            }
            restoredCount++
        }
        return restoredCount
    }

    private fun DiaryEntity.syncKey(): String = listOf(
        createdAt,
        content,
        color,
        positionX.toString(),
        positionY.toString(),
        zIndex.toString()
    ).joinToString("|")

    suspend fun saveDiariesToLocalAndRemote(diaries: List<DiaryEntity>): Boolean {
        return try {
            for (diary in diaries) {
                val existingDiary = diary.diaryId.takeIf { it > 0 }
                    ?.let { db.diaryDao().getDiaryById(it) }
                val reminderEnabled = diary.reviewCycleDays != ReminderSchedulePolicy.DISABLED
                val keepExistingSchedule = reminderEnabled &&
                    existingDiary?.reviewCycleDays == diary.reviewCycleDays &&
                    existingDiary.reviewCyclePattern == diary.reviewCyclePattern &&
                    existingDiary.reviewRepeatLast == diary.reviewRepeatLast &&
                    existingDiary.reminderHour == diary.reminderHour &&
                    existingDiary.reminderMinute == diary.reminderMinute &&
                    existingDiary.reminderAnchorAt > 0L
                val diaryToSave = diary.copy(
                    reminderAnchorAt = when {
                        !reminderEnabled -> 0L
                        keepExistingSchedule -> existingDiary.reminderAnchorAt
                        else -> System.currentTimeMillis()
                    },
                    reminderStage = if (keepExistingSchedule) existingDiary.reminderStage else 0,
                    lastRemindedAt = if (keepExistingSchedule) existingDiary.lastRemindedAt else 0L
                )
                // 1. Room 로컬 DB 저장
                val savedId = db.diaryDao().insertPostIt(diaryToSave)
                val updatedDiary = diaryToSave.copy(diaryId = savedId.toInt())

                if (reminderEnabled) {
                    ReminderScheduler.schedule(appContext, updatedDiary)
                } else {
                    ReminderScheduler.cancel(appContext, updatedDiary.diaryId)
                }

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
