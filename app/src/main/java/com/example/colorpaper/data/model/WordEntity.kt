package com.example.colorpaper.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["folder_id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folder_id"])]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "word_id") val wordId: Int = 0,
    @ColumnInfo(name = "folder_id") val folderId: Int,
    @ColumnInfo(name = "word_question") val wordQuestion: String, // 앞면 (퀴즈/마스킹)
    @ColumnInfo(name = "word_answer") val wordAnswer: String,     // 뒷면 (정답)
    @ColumnInfo(name = "is_memorized") val isMemorized: Boolean = false,
    @ColumnInfo(name = "interval") val interval: Int = 1,
    @ColumnInfo(name = "ease_factor") val easeFactor: Float = 2.5f,// 💡 암기 완료 여부 (플립 학습용)
    @ColumnInfo(name = "repetitions") val repetitions: Int = 0,    // 연속 성공 횟수
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long = 0L, // 다음 복습 일시
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long = 0L // 마지막 답변 일시
)
