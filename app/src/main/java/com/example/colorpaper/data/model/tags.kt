package com.example.colorpaper.data.model

import androidx.room.*


@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "tag_id") val tagId: Int = 0,
    @ColumnInfo(name = "tag_name") val tagName: String
)

// 다이어리와 태그를 연결해주는 중간 테이블 (N:M 관계 해소)
@Entity(
    tableName = "diary_tags",
    primaryKeys = ["diary_id", "tag_id"]
)
data class DiaryTagEntity(
    @ColumnInfo(name = "diary_id") val diaryId: Int,
    @ColumnInfo(name = "tag_id") val tagId: Int
)