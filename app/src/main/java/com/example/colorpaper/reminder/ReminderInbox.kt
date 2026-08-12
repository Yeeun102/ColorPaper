package com.example.colorpaper.reminder

import com.example.colorpaper.data.local.DiaryDao
import com.example.colorpaper.data.model.DiaryEntity
import java.util.Calendar

data class PendingReminder(
    val diary: DiaryEntity,
    val stage: Int,
    val elapsedDays: Long
)

object ReminderInbox {
    suspend fun pendingToday(dao: DiaryDao, userId: String, now: Long = System.currentTimeMillis()): List<PendingReminder> {
        if (userId.isBlank()) return emptyList()
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val pending = mutableListOf<PendingReminder>()
        for (diary in dao.getReminderEnabledDiaries().filter { it.userId == userId }) {
                val stage = when {
                    diary.lastRemindedAt >= startOfDay && diary.reminderStage > 0 -> diary.reminderStage - 1
                    ReminderSchedulePolicy.plannedTriggerAt(
                        diary.reminderAnchorAt, diary.reviewCycleDays, diary.reminderStage,
                        diary.reviewCyclePattern, diary.reviewRepeatLast,
                        diary.reminderHour, diary.reminderMinute
                    )?.let { it in startOfDay..now } == true -> diary.reminderStage
                    else -> continue
                }
                if (dao.getReminderAnswer(diary.diaryId, stage) != null) continue
                val elapsed = ReminderSchedulePolicy.elapsedDays(
                    diary.reviewCycleDays, stage, diary.reviewCyclePattern, diary.reviewRepeatLast
                ) ?: continue
                pending += PendingReminder(diary, stage, elapsed)
        }
        return pending.sortedByDescending { it.diary.lastRemindedAt }
    }
}
