package com.example.colorpaper.data.model

import androidx.room.*
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties // Firestore에 없는 필드가 추가되어 있어도 에러 안 나게 방어
@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "diary_id")
    var diaryId: Int = 0,

    @ColumnInfo(name = "user_id")
    var userId: String = "", // 💡 기본값 "" 지정 필수!

    @ColumnInfo(name = "content")
    var content: String = "", // 💡 기본값 "" 지정 필수!

    @ColumnInfo(name = "color")
    var color: String = "orange",

    @ColumnInfo(name = "tag")
    var tag: String = "",

    @ColumnInfo(name = "emotion_stamp")
    var emotionStamp: String? = null, // 💡 기본값 null 지정!

    @ColumnInfo(name = "is_highlighted")
    var isHighlighted: Boolean = false,

    @ColumnInfo(name = "visibility")
    var visibility: String = "PRIVATE",

    @ColumnInfo(name = "theme_id")
    var themeId: Int = 0,

    @ColumnInfo(name = "review_cycle_days")
    var reviewCycleDays: Int = 0,

    @ColumnInfo(name = "last_reminded_at")
    var lastRemindedAt: Long = 0,

    @ColumnInfo(name = "reminder_anchor_at")
    var reminderAnchorAt: Long = 0,

    @ColumnInfo(name = "reminder_stage")
    var reminderStage: Int = 0,

    @ColumnInfo(name = "reminder_end_date")
    var reminderEndDate: Int = 0,

    @ColumnInfo(name = "created_at")
    var createdAt: String = "", // 💡 기본값 "" 지정 필수!

    @ColumnInfo(name = "position_x")
    var positionX: Float = 0f,

    @ColumnInfo(name = "position_y")
    var positionY: Float = 0f,

    @ColumnInfo(name = "highlight_ranges")
    var highlightRanges: String = ""
) {
    // Firestore 파싱용 빈 생성자
    constructor() : this(0, "", "", "orange", "", null, false, "PRIVATE", 0, 0, 0, 0, 0, 0, "", 0f, 0f, "")
}