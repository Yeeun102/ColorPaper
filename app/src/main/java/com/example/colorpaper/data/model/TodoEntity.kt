package com.example.colorpaper.data.model

import androidx.room.*


@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "todo_id") val todoId: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int, // 외래키 역할
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "target_date") val targetDate: String // 💡 "2026-07-20" 형태로 저장해야 홈 화면/캘린더 띄우기 편함
)