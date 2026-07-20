package com.example.colorpaper.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

// 1. 단어장 세트 엔티티 (단어장보기 목록용)
@Entity(tableName = "flashcard_sets")
data class FlashcardSet(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val userId: Int,
    val title: String,            // 예: #set1
    val visibility: String = "전체공개" // 전체공개, 팔로워공개, 비공개
)

// 2. 단어 카드 엔티티
@Entity(
    tableName = "flashcard_items",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardSet::class,
            parentColumns = ["setId"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["setId"])]
)
data class FlashcardItem(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0,
    val setId: Long,             // 소속 단어장 ID
    val question: String,        // 앞면 내용
    val answer: String,          // 뒷면 내용
    val interval: Int = 1,       // Anki 반복 주기(일 단위)
    val easeFactor: Float = 2.5f // Anki 난이도 계수
)