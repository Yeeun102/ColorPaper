package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.WordEntity

@Dao
interface FlashcardDao {

    // ==========================================
    // 1. 단어장 세트(FlashcardSet) 관련 쿼리
    // ==========================================

    @Query("SELECT * FROM folders ORDER BY folder_id DESC")
    fun getAllSets(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolder(flashcardSet: FolderEntity): Long


    // ==========================================
    // 2. 단어 카드(FlashcardItem) 관련 쿼리
    // ==========================================

    @Query("SELECT * FROM words WHERE folder_id = :targetSetId ORDER BY next_review_at ASC, word_id ASC")
    fun getItemsBySetId(targetSetId: Long): List<WordEntity>

    @Query("SELECT * FROM folders WHERE user_id = :userId ORDER BY folder_id DESC")
    fun getAllSetsByUserId(userId: String): List<FolderEntity>

    @Update
    suspend fun updateWord(word: WordEntity)

    @Query(
        """
        SELECT COUNT(*) FROM words
        INNER JOIN folders ON folders.folder_id = words.folder_id
        WHERE folders.user_id = :userId
          AND words.last_reviewed_at >= :startOfDay
          AND words.last_reviewed_at < :startOfNextDay
        """
    )
    suspend fun countReviewedCardsBetween(
        userId: String,
        startOfDay: Long,
        startOfNextDay: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllItems(items: List<WordEntity>)

    @Delete
    fun deleteSet(flashcardSet: FolderEntity)

    // 🌟 userId 타입을 Int -> String으로 수정하여 FolderEntity의 userId(String)와 일치시킵니다.
    @Query("SELECT * FROM folders WHERE user_id = :userId AND visibility = :visibility")
    suspend fun getFlashcardSetsByVisibility(userId: String, visibility: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE visibility = '전체공개'")
    suspend fun getSharedFlashcardSets(): List<FolderEntity>
}
