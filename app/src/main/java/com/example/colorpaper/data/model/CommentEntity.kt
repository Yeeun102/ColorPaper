package com.example.colorpaper.data.model

import androidx.room.*

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "comment_id") val commentId: Int = 0,
    @ColumnInfo(name = "diary_id") val diaryId: Int,
    @ColumnInfo(name = "user_id") val userId: Int, // 댓글 작성자 (나 혹은 친구)
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)