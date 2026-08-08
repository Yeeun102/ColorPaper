package com.example.colorpaper.data.local

import androidx.room.*
import com.example.colorpaper.data.model.HighlightEntity

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlight_table ORDER BY highlightId DESC")
    suspend fun getAllHighlights(): List<HighlightEntity>

    @Query("SELECT * FROM highlight_table WHERE diaryId = :diaryId")
    suspend fun getHighlightsByDiaryId(diaryId: Int): List<HighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)
}