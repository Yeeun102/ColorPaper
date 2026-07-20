package com.example.colorpaper.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.colorpaper.data.model.DiaryComment
import com.example.colorpaper.data.model.DiaryPostIt
import com.example.colorpaper.data.model.FlashcardItem
import com.example.colorpaper.data.model.FlashcardSet

@Database(entities = [FlashcardSet::class, FlashcardItem::class, DiaryPostIt::class, DiaryComment::class],
    version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun flashcardDao(): FlashcardDao
    abstract fun diaryDao(): DiaryDao

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
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}