package com.example.colorpaper.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryEntity
import com.example.colorpaper.data.model.DiaryTagEntity
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.FriendEntity
import com.example.colorpaper.data.model.HighlightEntity
import com.example.colorpaper.data.model.InteractionEntity
import com.example.colorpaper.data.model.ReminderAnswerEntity
import com.example.colorpaper.data.model.TagEntity
import com.example.colorpaper.data.model.TodoEntity
import com.example.colorpaper.data.model.UserEntity
import com.example.colorpaper.data.model.WordEntity

@Database(
    entities = [
        FolderEntity::class,
        WordEntity::class,
        UserEntity::class,
        TodoEntity::class,
        DiaryEntity::class,
        CommentEntity::class,
        InteractionEntity::class,
        FriendEntity::class,
        TagEntity::class,
        DiaryTagEntity::class,
        HighlightEntity::class,
        ReminderAnswerEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun flashcardDao(): FlashcardDao
    abstract fun userDao(): UserDao
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
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .allowMainThreadQueries()
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE diaries ADD COLUMN reminder_anchor_at INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE diaries ADD COLUMN reminder_stage INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminder_answers (
                        answer_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        diary_id INTEGER NOT NULL,
                        reminder_stage INTEGER NOT NULL,
                        question TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        answered_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_reminder_answers_diary_id_reminder_stage
                    ON reminder_answers (diary_id, reminder_stage)
                    """.trimIndent()
                )
            }
        }
    }
}
