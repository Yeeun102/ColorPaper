package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.colorpaper.data.model.FlashcardItem
import com.example.colorpaper.data.model.FlashcardSet

@Dao
interface FlashcardDao {

    // ==========================================
    // 1. 단어장 세트(FlashcardSet) 관련 쿼리
    // ==========================================

    // 내 단어장 목록 화면에 뿌려줄 모든 세트 조회
    @Query("SELECT * FROM flashcard_sets ORDER BY setId DESC")
    fun getAllSets(): List<FlashcardSet>

    // 새 단어장 저장 (성공 시 생성된 세트의 행 ID(Long)를 반환함)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSet(flashcardSet: FlashcardSet): Long


    // ==========================================
    // 2. 단어 카드(FlashcardItem) 관련 쿼리
    // ==========================================

    // 학습 화면에서 특정 단어장(setId)에 속한 카드들만 필터링해서 조회
    @Query("SELECT * FROM flashcard_items WHERE setId = :targetSetId")
    fun getItemsBySetId(targetSetId: Long): List<FlashcardItem>

    // CSV 파싱이나 수동 입력으로 만든 카드 리스트를 한 번에 통째로 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllItems(items: List<FlashcardItem>)

    @Delete
    fun deleteSet(flashcardSet: FlashcardSet)

    @Query("SELECT * FROM flashcard_sets WHERE userId = :userId AND visibility = :visibility")
    suspend fun getFlashcardSetsByVisibility(userId: Int, visibility: String): List<FlashcardSet>

    @Query("SELECT * FROM flashcard_sets WHERE visibility = '전체공개'")
    suspend fun getSharedFlashcardSets(): List<FlashcardSet>
}
