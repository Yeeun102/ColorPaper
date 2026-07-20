package com.example.colorpaper.data.model

import androidx.room.*

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "word_id") val wordId: Int = 0,
    @ColumnInfo(name = "folder_id") val folderId: Int,
    @ColumnInfo(name = "word_question") val wordQuestion: String, // 앞면 (퀴즈/마스킹)
    @ColumnInfo(name = "word_answer") val wordAnswer: String,     // 뒷면 (정답)
    @ColumnInfo(name = "is_memorized") val isMemorized: Boolean = false // 💡 암기 완료 여부 (플립 학습용)
)