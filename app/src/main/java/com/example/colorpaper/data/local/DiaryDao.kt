package com.example.colorpaper.data.local

import androidx.room.*
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.HighlightEntity
import com.example.colorpaper.data.model.ReminderAnswerEntity

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries WHERE diary_id = :diaryId LIMIT 1")
    suspend fun getDiaryById(diaryId: Int): DiaryEntity?

    @Query("SELECT * FROM diaries WHERE review_cycle_days != 0")
    suspend fun getReminderEnabledDiaries(): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE user_id = :userId")
    suspend fun getDiariesByUserId(userId: Int): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE user_id = :userId AND visibility = :visibility")
    suspend fun getDiariesByVisibility(userId: Int, visibility: String): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE created_at LIKE :yearMonth || '%' ORDER BY created_at ASC")
    suspend fun getDiariesForMonth(yearMonth: String): List<DiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostIt(diary: DiaryEntity): Long

    @Query("UPDATE diaries SET last_reminded_at = :triggeredAt, reminder_stage = :nextStage WHERE diary_id = :diaryId")
    suspend fun markReminderTriggered(diaryId: Int, triggeredAt: Long, nextStage: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReminderAnswer(answer: ReminderAnswerEntity)

    @Query("SELECT * FROM reminder_answers WHERE diary_id = :diaryId AND reminder_stage = :stage LIMIT 1")
    suspend fun getReminderAnswer(diaryId: Int, stage: Int): ReminderAnswerEntity?

    @Query("SELECT * FROM diaries WHERE created_at = :targetDate")
    fun getPostItsByDate(targetDate: String): List<DiaryEntity>

    @Query("SELECT * FROM comments WHERE date = :targetDate ORDER BY comment_id ASC")
    fun getCommentsByDate(targetDate: String): List<CommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComment(comment: CommentEntity): Long

    @Delete
    suspend fun deleteDiary(diary: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Query("SELECT * FROM highlight_table WHERE date = :date")
    suspend fun getHighlightsByDate(date: String): List<HighlightEntity>

    @Query("SELECT * FROM highlight_table ORDER BY highlightId DESC")
    suspend fun getAllHighlights(): List<HighlightEntity> // 다른 화면에서 모아볼 때 사용
}
