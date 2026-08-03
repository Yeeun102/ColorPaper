package com.example.colorpaper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.colorpaper.data.local.entity.DiaryCommentEntity

@Dao
interface DiaryCommentDao {

    // 댓글 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: DiaryCommentEntity)

    // 특정 다이어리에 달린 댓글 목록 가져오기
    @Query("SELECT * FROM diary_comments WHERE diaryId = :diaryId ORDER BY commentId DESC")
    suspend fun getCommentsByDiaryId(diaryId: Long): List<DiaryCommentEntity>

    // 특정 사용자가 받은 댓글 목록 가져오기 (알림용)
    @Query("SELECT * FROM diary_comments WHERE ownerId = :ownerId ORDER BY commentId DESC")
    suspend fun getCommentsForOwner(ownerId: Int): List<DiaryCommentEntity>

    // 댓글 삭제
    @Query("DELETE FROM diary_comments WHERE commentId = :commentId")
    suspend fun deleteComment(commentId: Long)
}