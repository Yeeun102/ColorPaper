package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.colorpaper.data.model.WidgetEntity

@Dao
interface WidgetDao {
    
    // 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidgets(widgets: List<WidgetEntity>) : Unit

    // 수정
    @Update
    suspend fun updateWidget(widget: WidgetEntity) : Unit

    // 불러오기
    @Query("SELECT * FROM widget_table WHERE user_id = :userId ORDER BY widget_order ASC")
    suspend fun getWidgetsByUser(userId: Int): List<WidgetEntity>
}