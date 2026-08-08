package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.colorpaper.data.model.TodoEntity

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE user_id = :userId AND target_date = :date ORDER BY todo_id ASC")
    suspend fun getTodosForDate(userId: Int, date: String): List<TodoEntity>

    @Query(
        """
        SELECT * FROM todos
        WHERE user_id = :userId
          AND target_date < :date
          AND is_completed = 0
          AND carry_over = 1
        ORDER BY target_date DESC, todo_id ASC
        """
    )
    suspend fun getCarryOverCandidates(userId: Int, date: String): List<TodoEntity>

    @Insert
    suspend fun insertTodo(todo: TodoEntity): Long

    @Query("UPDATE todos SET is_completed = :completed WHERE todo_id = :todoId")
    suspend fun updateCompletion(todoId: Int, completed: Boolean)

    @Query("UPDATE todos SET carry_over = :carryOver WHERE todo_id = :todoId")
    suspend fun updateCarryOver(todoId: Int, carryOver: Boolean)

    @Query("UPDATE todos SET target_date = :targetDate WHERE todo_id = :todoId")
    suspend fun moveToDate(todoId: Int, targetDate: String)
}
