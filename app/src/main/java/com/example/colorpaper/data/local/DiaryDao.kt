package com.example.colorpaper.data.local

import androidx.room.*
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.CommentEntity

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries WHERE user_id = :userId")
    suspend fun getDiariesByUserId(userId: Int): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE user_id = :userId AND visibility = :visibility")
    suspend fun getDiariesByVisibility(userId: Int, visibility: String): List<DiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostIt(diary: DiaryEntity): Long

    @Query("SELECT * FROM diaries WHERE created_at = :targetDate")
    fun getPostItsByDate(targetDate: String): List<DiaryEntity>

    @Query("SELECT * FROM comments WHERE date = :targetDate ORDER BY comment_id ASC")
    fun getCommentsByDate(targetDate: String): List<CommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComment(comment: CommentEntity): Long

    @Delete
    suspend fun deleteDiary(diary: DiaryEntity)
}
