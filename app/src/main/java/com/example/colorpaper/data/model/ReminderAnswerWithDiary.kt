package com.example.colorpaper.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class ReminderAnswerWithDiary(
    @Embedded val reminderAnswer: ReminderAnswerEntity,
    @ColumnInfo(name = "diary_content") val diaryContent: String?,
    @ColumnInfo(name = "diary_created_at") val diaryCreatedAt: String?
)
