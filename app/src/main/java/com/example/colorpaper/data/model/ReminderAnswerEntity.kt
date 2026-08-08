package com.example.colorpaper.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_answers",
    indices = [Index(value = ["diary_id", "reminder_stage"], unique = true)]
)
data class ReminderAnswerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "answer_id") val answerId: Int = 0,
    @ColumnInfo(name = "diary_id") val diaryId: Int,
    @ColumnInfo(name = "reminder_stage") val reminderStage: Int,
    @ColumnInfo(name = "question") val question: String,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "answered_at") val answeredAt: Long = System.currentTimeMillis()
)
