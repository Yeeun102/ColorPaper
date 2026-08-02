package com.example.colorpaper.data.model

import androidx.room.*

@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "diary_id") val diaryId: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "color") val color: String = "orange", // 💡 [기존 코드 통합] 포스트잇 배경 색상
    @ColumnInfo(name = "tag") val tag: String = "",
    @ColumnInfo(name = "emotion_stamp") val emotionStamp: String?, // 감정 이모지 스탬프
    @ColumnInfo(name = "is_highlighted") val isHighlighted: Boolean = false,
    @ColumnInfo(name = "visibility") val visibility: String = "PRIVATE", // PRIVATE, PUBLIC(친구공유)
    @ColumnInfo(name = "theme_id") val themeId: Int = 0,
    @ColumnInfo(name = "review_cycle_days") val reviewCycleDays: Int = 0, // -1: 망각 곡선, 0: 사용 안 함, 양수: 사용자 지정 주기
    @ColumnInfo(name = "last_reminded_at") val lastRemindedAt: Long = 0, // 💡 마지막 알림 발송 시간 (망각곡선용)
    @ColumnInfo(name = "reminder_anchor_at") val reminderAnchorAt: Long = 0,
    @ColumnInfo(name = "reminder_stage") val reminderStage: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String, // 💡 "2026-07-20" (캘린더 매핑용 문자열)
    @ColumnInfo(name = "position_x") val positionX: Float = 0f,
    @ColumnInfo(name = "position_y") val positionY: Float = 0f,
    @ColumnInfo (name = "highlight_ranges") val highlightRanges: String = ""
)
