package com.example.colorpaper.data.repository

import com.example.colorpaper.data.local.DiaryDao
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.ReminderAnswerEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReminderAnswerStore(
    private val dao: DiaryDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun get(userId: String, diaryId: Int, stage: Int): ReminderAnswerEntity? {
        dao.getReminderAnswer(diaryId, stage)?.let { return it }
        return readRemote(userId, diaryId, stage)?.also { dao.saveReminderAnswer(it) }
    }

    suspend fun getLatestBeforeStage(
        userId: String,
        diaryId: Int,
        stage: Int
    ): ReminderAnswerEntity? {
        dao.getLatestReminderAnswerBeforeStage(diaryId, stage)?.let { return it }
        for (previousStage in stage - 1 downTo 0) {
            readRemote(userId, diaryId, previousStage)?.let {
                dao.saveReminderAnswer(it)
                return it
            }
        }
        return null
    }

    suspend fun save(userId: String, diary: DiaryEntity, answer: ReminderAnswerEntity) {
        firestore.collection(COLLECTION)
            .document(documentId(userId, diary.diaryId, answer.reminderStage))
            .set(
                mapOf(
                    "userId" to userId,
                    "diaryId" to diary.diaryId,
                    "diaryCreatedAt" to diary.createdAt,
                    "reminderStage" to answer.reminderStage,
                    "question" to answer.question,
                    "answer" to answer.answer,
                    "answeredAt" to answer.answeredAt
                )
            )
            .await()
        dao.saveReminderAnswer(answer)
    }

    suspend fun syncUserAnswers(userId: String) {
        val snapshot = firestore.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .get()
            .await()
        snapshot.documents.mapNotNull(::toAnswer).forEach { dao.saveReminderAnswer(it) }
    }

    private suspend fun readRemote(
        userId: String,
        diaryId: Int,
        stage: Int
    ): ReminderAnswerEntity? = firestore.collection(COLLECTION)
        .document(documentId(userId, diaryId, stage))
        .get()
        .await()
        .takeIf { it.exists() }
        ?.let(::toAnswer)

    private fun toAnswer(document: DocumentSnapshot): ReminderAnswerEntity? {
        val diaryId = document.getLong("diaryId")?.toInt() ?: return null
        val stage = document.getLong("reminderStage")?.toInt() ?: return null
        val question = document.getString("question") ?: return null
        val answer = document.getString("answer") ?: return null
        return ReminderAnswerEntity(
            diaryId = diaryId,
            reminderStage = stage,
            question = question,
            answer = answer,
            answeredAt = document.getLong("answeredAt") ?: 0L
        )
    }

    private fun documentId(userId: String, diaryId: Int, stage: Int): String =
        "${userId}_${diaryId}_$stage"

    private companion object {
        const val COLLECTION = "reminder_answers"
    }
}
