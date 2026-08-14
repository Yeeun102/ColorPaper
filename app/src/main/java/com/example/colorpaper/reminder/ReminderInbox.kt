package com.example.colorpaper.reminder

import com.example.colorpaper.data.local.DiaryDao
import com.example.colorpaper.data.model.DiaryEntity

data class PendingReminder(
    val diary: DiaryEntity,
    val stage: Int,
    val elapsedDays: Long
)

object ReminderInbox {
    suspend fun pendingToday(dao: DiaryDao, userId: String, now: Long = System.currentTimeMillis()): List<PendingReminder> {
        if (userId.isBlank()) return emptyList()
        val pending = mutableListOf<PendingReminder>()
        for (diary in dao.getReminderEnabledDiaries().filter { it.userId == userId }) {
            val unansweredStages = mutableSetOf<Int>()

            for (deliveredStage in 0 until diary.reminderStage) {
                if (dao.getReminderAnswer(diary.diaryId, deliveredStage) == null) {
                    unansweredStages += deliveredStage
                }
            }

            val currentStageDue = ReminderSchedulePolicy.plannedTriggerAt(
                diary.reminderAnchorAt, diary.reviewCycleDays, diary.reminderStage,
                diary.reviewCyclePattern, diary.reviewRepeatLast,
                diary.reminderHour, diary.reminderMinute, diary.reminderEndDate
            )?.let { it <= now } == true
            if (currentStageDue && dao.getReminderAnswer(diary.diaryId, diary.reminderStage) == null) {
                unansweredStages += diary.reminderStage
            }

            for (stage in unansweredStages.sorted()) {
                val elapsed = ReminderSchedulePolicy.elapsedDays(
                    diary.reviewCycleDays, stage, diary.reviewCyclePattern, diary.reviewRepeatLast
                ) ?: continue
                pending += PendingReminder(diary, stage, elapsed)
            }
        }
        return pending.sortedWith(
            compareByDescending<PendingReminder> { it.diary.lastRemindedAt }
                .thenBy { it.stage }
        )
    }
}
