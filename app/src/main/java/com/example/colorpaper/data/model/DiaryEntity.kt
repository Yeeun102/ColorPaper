package com.example.colorpaper.data.model

import androidx.room.*

@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "diary_id") val diaryId: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "emotion_stamp") val emotionStamp: String?, // 감정 이모지 스탬프
    @ColumnInfo(name = "visibility") val visibility: String = "PRIVATE", // PRIVATE, PUBLIC(친구공유)
    @ColumnInfo(name = "theme_id") val themeId: Int = 0,
    @ColumnInfo(name = "review_cycle_days") val reviewCycleDays: Int = 0, // 💡 복습 주기 (0: 설정안함, 1: 1일후, 7: 7일후 등)
    @ColumnInfo(name = "last_reminded_at") val lastRemindedAt: Long = 0, // 💡 마지막 알림 발송 시간 (망각곡선용)
    @ColumnInfo(name = "created_at") val createdAt: String // 💡 "2026-07-20" (캘린더 매핑용 문자열)
)