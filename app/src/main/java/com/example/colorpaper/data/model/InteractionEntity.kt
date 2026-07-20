package com.example.colorpaper.data.model

import androidx.room.*

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "interaction_id") val interactionId: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,   // 반응 남긴 사람
    @ColumnInfo(name = "diary_id") val diaryId: Int, // 대상 다이어리
    @ColumnInfo(name = "interaction_type") val interactionType: String // LIKE, CONGRATS 등
)