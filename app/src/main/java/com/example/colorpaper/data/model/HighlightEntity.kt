package com.example.colorpaper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlight_table")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val highlightId: Int = 0,
    val diaryId: Int,          // 원본 일기 포스트잇 ID
    val date: String,          // yyyy-MM-dd 날짜 정보
    val highlightedText: String // 형광펜 칠한 텍스트 내용
)