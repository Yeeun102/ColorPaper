package com.example.colorpaper.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.colorpaper.data.model.*

@Database(
    entities = [
        FlashcardSet::class,
        FlashcardItem::class,
        UserEntity::class,
        TodoEntity::class,
        DiaryEntity::class,
        CommentEntity::class,
        InteractionEntity::class,
        FriendEntity::class,
        TagEntity::class,
        DiaryTagEntity::class
    ],
    version = 3,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {

    abstract fun flashcardDao(): FlashcardDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashcard_database"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}