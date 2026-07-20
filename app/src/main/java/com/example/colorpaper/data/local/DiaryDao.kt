package com.example.colorpaper.data.local

import androidx.room.*
import com.example.colorpaper.data.model.DiaryEntity

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries WHERE user_id = :userId")
    suspend fun getDiariesByUserId(userId: Int): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE user_id = :userId AND visibility = :visibility")
    suspend fun getDiariesByVisibility(userId: Int, visibility: String): List<DiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: DiaryEntity)

    @Delete
    suspend fun deleteDiary(diary: DiaryEntity)
}
