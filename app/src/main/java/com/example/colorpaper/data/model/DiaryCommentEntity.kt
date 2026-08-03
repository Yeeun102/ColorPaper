package com.example.colorpaper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_comments")
data class DiaryCommentEntity(
    @PrimaryKey(autoGenerate = true)
    val commentId: Long = 0L,         // 댓글 고유 ID (기본값 0L)
    val diaryId: Long = 0L,           // 해당 다이어리 ID (기본값 0L)
    val writerId: Int = 0,            // 작성자 ID (기본값 0)
    val writerName: String = "",      // 작성자 닉네임 (기본값 "")
    val ownerId: Int = 0,             // 다이어리 소유자 ID (기본값 0)
    val emoji: String = "😂",          // 이모지 스탬프 (기본값 "😂")
    val content: String = "",         // 댓글 내용 (기본값 "")
    val createdAt: String = ""        // 작성 시간 (기본값 "")
)