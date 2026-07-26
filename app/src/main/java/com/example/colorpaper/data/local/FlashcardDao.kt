package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.WordEntity

@Dao
interface FlashcardDao {

    // ==========================================
    // 1. 단어장 세트(FlashcardSet) 관련 쿼리
    // ==========================================

    // 내 단어장 목록 화면에 뿌려줄 모든 세트 조회
    @Query("SELECT * FROM folders ORDER BY folder_id DESC")
    fun getAllSets(): List<FolderEntity>

    // 새 단어장 저장 (성공 시 생성된 세트의 행 ID(Long)를 반환함)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolder(flashcardSet: FolderEntity): Long


    // ==========================================
    // 2. 단어 카드(FlashcardItem) 관련 쿼리
    // ==========================================

    // 학습 화면에서 특정 단어장(setId)에 속한 카드들만 필터링해서 조회
    @Query("SELECT * FROM words WHERE folder_id = :targetSetId")
    fun getItemsBySetId(targetSetId: Long): List<WordEntity>

    // CSV 파싱이나 수동 입력으로 만든 카드 리스트를 한 번에 통째로 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllItems(items: List<WordEntity>)

    @Delete
    fun deleteSet(flashcardSet: FolderEntity)

    @Query("SELECT * FROM folders WHERE user_id = :userId AND visibility = :visibility")
    suspend fun getFlashcardSetsByVisibility(userId: Int, visibility: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE visibility = '전체공개'")
    suspend fun getSharedFlashcardSets(): List<FolderEntity>
}
