package com.example.colorpaper.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// 위젯 종류
enum class WidgetType {
    CHECKLIST, TODO_LIST, YEARS_AGO, TIMELINE, CALENDAR, REMINDER
}

@Entity(tableName = "widget_table")
data class WidgetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "widget_id") val widgetId: Int = 0,

    @ColumnInfo(name = "user_id") val userId: Int, 
    @ColumnInfo(name = "widget_type") val type: String, 
    @ColumnInfo(name = "is_visible") var isVisible: Boolean,
    @ColumnInfo(name = "widget_order") var order: Int
)