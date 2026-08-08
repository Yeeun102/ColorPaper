package com.example.colorpaper.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_table")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "user_id") val userId: Int = 0,
    @ColumnInfo(name = "user_code") val userCode: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "nickname") val nickname: String,
    @ColumnInfo(name = "profile_image_url") val profileImageUrl: String?,
    @ColumnInfo(name = "membership_status") val membershipStatus: String = "FREE",
    @ColumnInfo(name = "push_enabled") val pushEnabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)