package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.TagEntity

@Dao
interface DiaryDao {
    // 💡 1. 'diaries' 테이블에서 created_at 날짜 컬럼으로 일기 조회
    @Query("SELECT * FROM diaries WHERE created_at = :targetDate")
    fun getPostItsByDate(targetDate: String): List<DiaryEntity>

    // 💡 2. 'comments' 테이블에서 date 날짜 컬럼으로 셀프 댓글 조회 (comment_id 오름차순)
    @Query("SELECT * FROM comments WHERE date = :targetDate ORDER BY comment_id ASC")
    fun getCommentsByDate(targetDate: String): List<CommentEntity>

    // 💡 3. DiaryEntity 삽입 (기존 insertPostIt 호환 또는 insertDiary)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDiary(diary: DiaryEntity): Long

    // 💡 이전 프래그먼트 코드와의 완벽한 호환성을 위해 기존 이름도 함께 유지 (선택사항)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPostIt(diary: DiaryEntity): Long

    // 💡 4. CommentEntity 삽입
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComment(comment: CommentEntity): Long

    @Query("SELECT tag_id FROM tags WHERE tag_name = :name")
    suspend fun getTagIdByName(name: String): Int?

    // 태그 삽입
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    // 다이어리-태그 연결 삽입
    //@Insert(onConflict = OnConflictStrategy.REPLACE)
    //suspend fun insertDiaryTag(diaryTag: DiaryTagEntity)

    // 특정 다이어리의 태그 목록 가져오기
    @Query("SELECT t.tag_name FROM tags t JOIN diary_tags dt ON t.tag_id = dt.tag_id WHERE dt.diary_id = :diaryId")
    suspend fun getTagsByDiaryId(diaryId: Int): List<String>
}