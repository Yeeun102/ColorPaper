package com.example.colorpaper.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.colorpaper.data.model.FriendEntity
import com.example.colorpaper.data.model.UserEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM user_table WHERE user_id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?


    @Query("""
        SELECT u.* FROM user_table u 
        INNER JOIN friend_table f ON u.user_id = f.friend_user_id 
        WHERE f.my_user_id = :myUserId
    """)
    suspend fun getMyFriends(myUserId: Int): List<UserEntity>

    @Query("""
        SELECT * FROM user_table 
        WHERE user_id != :myUserId 
          AND user_id NOT IN (SELECT friend_user_id FROM friend_table WHERE my_user_id = :myUserId)
          AND nickname LIKE '%' || :searchQuery || '%'
    """)
    suspend fun searchNewFriends(myUserId: Int, searchQuery: String): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFriend(friend: FriendEntity)

    @Delete
    suspend fun removeFriend(friend: FriendEntity)
}