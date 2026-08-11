package com.example.colorpaper.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.colorpaper.data.model.CommentEntity
import com.example.colorpaper.data.model.DiaryCommentEntity
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
import com.example.colorpaper.data.model.WidgetEntity
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
        ReminderAnswerEntity::class,
        DiaryCommentEntity::class,
        WidgetEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun flashcardDao(): FlashcardDao
    abstract fun userDao(): UserDao
    abstract fun diaryDao(): DiaryDao
    abstract fun todoDao(): TodoDao
    abstract fun widgetDao(): WidgetDao
    abstract fun diaryCommentDao(): DiaryCommentDao
    abstract fun highlightDao(): HighlightDao // 🌟 하이라이트 DAO 추가

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            android.util.Log.d("AppDatabase", "getDatabase() called")
            return INSTANCE ?: synchronized(this) {
                android.util.Log.d("AppDatabase", "Creating new database instance")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashcard_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                
                android.util.Log.d("AppDatabase", "Database instance built: $instance")
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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE todos ADD COLUMN carry_over INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS widget_table (
                        widget_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        user_id INTEGER NOT NULL,
                        widget_type TEXT NOT NULL,
                        is_visible INTEGER NOT NULL,
                        widget_order INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE words ADD COLUMN last_reviewed_at INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE comments ADD COLUMN is_checked INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasCheckedColumn = db.query("PRAGMA table_info(comments)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "is_checked") {
                            found = true
                            break
                        }
                    }
                    found
                }
                if (!hasCheckedColumn) {
                    db.execSQL("ALTER TABLE comments ADD COLUMN is_checked INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    """
                    CREATE TABLE diary_comments_new (
                        commentId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        diaryId INTEGER NOT NULL,
                        writerId INTEGER NOT NULL,
                        writerName TEXT NOT NULL,
                        ownerId INTEGER NOT NULL,
                        emoji TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO diary_comments_new (
                        commentId, diaryId, writerId, writerName,
                        ownerId, emoji, content, createdAt
                    )
                    SELECT
                        commentId, diaryId, writerId, writerName,
                        ownerId, emoji, content,
                        CASE
                            WHEN CAST(createdAt AS TEXT) NOT GLOB '*[^0-9]*'
                                THEN CAST(createdAt AS INTEGER)
                            ELSE COALESCE(CAST(strftime('%s', createdAt) AS INTEGER) * 1000, 0)
                        END
                    FROM diary_comments
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE diary_comments")
                db.execSQL("ALTER TABLE diary_comments_new RENAME TO diary_comments")
                db.execSQL("ALTER TABLE diaries ADD COLUMN reminder_hour INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE diaries ADD COLUMN reminder_minute INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN review_cycle_pattern TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN review_repeat_last INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
