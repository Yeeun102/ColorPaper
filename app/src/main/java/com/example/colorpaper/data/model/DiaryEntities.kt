package com.example.colorpaper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_post_its")
data class DiaryPostIt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                  // "yyyy-MM-dd"
    val content: String,               // 일기 내용
    val color: String = "orange",      // 포스트잇 배경 색상
    val tag: String = "",              // #일상, #업무 등
    val emotion: String = "",          // 선택된 감정
    val isHighlighted: Boolean = false // 형광펜 여부
)

@Entity(tableName = "diary_comments")
data class DiaryComment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                  // 부모 일기 날짜 ("yyyy-MM-dd")
    val content: String,               // 셀프 댓글 내용
    val color: String = "orange",      // 댓글 포스트잇 색상
    val timestamp: String              // "HH:mm" 작성 시간
)