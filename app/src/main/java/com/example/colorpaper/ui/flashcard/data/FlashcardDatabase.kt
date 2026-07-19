package com.example.colorpaper.ui.flashcard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FlashcardSet::class, FlashcardItem::class], version = 1, exportSchema = false)
abstract class FlashcardDatabase : RoomDatabase() {

    abstract fun flashcardDao(): FlashcardDao

    companion object {
        @Volatile
        private var INSTANCE: FlashcardDatabase? = null

        fun getDatabase(context: Context): FlashcardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlashcardDatabase::class.java,
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