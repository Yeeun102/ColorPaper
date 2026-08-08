package com.example.colorpaper.data.local

import androidx.room.*
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.HighlightEntity
import com.example.colorpaper.data.model.ReminderAnswerEntity
import com.example.colorpaper.data.model.ReminderAnswerWithDiary

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

    @Query("SELECT * FROM diaries WHERE created_at BETWEEN :startDate AND :endDate ORDER BY created_at ASC")
    suspend fun getDiariesBetween(startDate: String, endDate: String): List<DiaryEntity>

    @Query(
        """
        SELECT * FROM diaries
        WHERE user_id = :userId
          AND created_at BETWEEN :startDate AND :endDate
        ORDER BY created_at ASC
        """
    )
    suspend fun getDiariesBetweenByUserId(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostIt(diary: DiaryEntity): Long

    // 🌟 UPDATE 쿼리 끝에 반환 타입 : Int 명시
    @Query("UPDATE diaries SET last_reminded_at = :triggeredAt, reminder_stage = :nextStage WHERE diary_id = :diaryId")
    suspend fun markReminderTriggered(diaryId: Int, triggeredAt: Long, nextStage: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReminderAnswer(answer: ReminderAnswerEntity)

    @Query("SELECT * FROM reminder_answers WHERE diary_id = :diaryId AND reminder_stage = :stage LIMIT 1")
    suspend fun getReminderAnswer(diaryId: Int, stage: Int): ReminderAnswerEntity?

    @Query(
        """
        SELECT reminder_answers.*,
               diaries.content AS diary_content,
               diaries.created_at AS diary_created_at
        FROM reminder_answers
        LEFT JOIN diaries ON diaries.diary_id = reminder_answers.diary_id
        WHERE reminder_answers.answered_at >= :startOfDay
          AND reminder_answers.answered_at < :startOfNextDay
          AND diaries.user_id = :userId
        ORDER BY reminder_answers.answered_at DESC
        """
    )
    suspend fun getReminderAnswersBetween(
        userId: String,
        startOfDay: Long,
        startOfNextDay: Long
    ): List<ReminderAnswerWithDiary>

    @Query(
        """
        SELECT * FROM diaries
        WHERE user_id = :userId
          AND substr(created_at, 6, 5) = :monthAndDay
          AND created_at < :today
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    suspend fun getLatestDiaryFromSameDay(
        userId: String,
        monthAndDay: String,
        today: String
    ): DiaryEntity?

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
    suspend fun getAllHighlights(): List<HighlightEntity>

    @Query("SELECT * FROM diaries WHERE created_at = :date AND user_id = :userId")
    suspend fun getPostItsByDateAndUserId(date: String, userId: String): List<DiaryEntity>

    @Query("SELECT * FROM comments WHERE date = :targetDate AND user_id = :userId ORDER BY comment_id ASC")
    fun getCommentsByDateAndUserId(targetDate: String, userId: String): List<CommentEntity>

    @Query("SELECT * FROM diaries WHERE visibility = :visibility ORDER BY created_at DESC")
    suspend fun getPublicDiaries(visibility: String = "전체공개"): List<DiaryEntity>

    @Query("UPDATE diaries SET is_highlighted = :isHighlighted WHERE created_at = :date AND user_id = :userId")
    suspend fun updateHighlightByDate(date: String, userId: String, isHighlighted: Boolean)
}
