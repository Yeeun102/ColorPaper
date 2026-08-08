package com.example.colorpaper.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "friend_table",
    primaryKeys = ["my_user_id", "friend_user_id"]
)
data class FriendEntity(
    @ColumnInfo(name = "my_user_id") val myUserId: Int,
    @ColumnInfo(name = "friend_user_id") val friendUserId: Int
)