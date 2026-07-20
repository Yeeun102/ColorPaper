package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.colorpaper.data.model.DiaryComment
import com.example.colorpaper.data.model.DiaryPostIt

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_post_its WHERE date = :targetDate")
    fun getPostItsByDate(targetDate: String): List<DiaryPostIt>

    @Query("SELECT * FROM diary_comments WHERE date = :targetDate ORDER BY id ASC")
    fun getCommentsByDate(targetDate: String): List<DiaryComment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPostIt(postIt: DiaryPostIt): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComment(comment: DiaryComment): Long
}