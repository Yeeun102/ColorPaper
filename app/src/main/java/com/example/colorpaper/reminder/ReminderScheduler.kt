package com.example.colorpaper.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.colorpaper.data.model.DiaryEntity

object ReminderScheduler {
    fun schedule(context: Context, diary: DiaryEntity) {
        val triggerAt = ReminderSchedulePolicy.nextTriggerAt(
            anchorAt = diary.reminderAnchorAt,
            cycleDays = diary.reviewCycleDays,
            stage = diary.reminderStage,
            cyclePattern = diary.reviewCyclePattern,
            repeatLast = diary.reviewRepeatLast,
            hour = diary.reminderHour,
            minute = diary.reminderMinute
        ) ?: return cancel(context, diary.diaryId)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            alarmPendingIntent(context, diary.diaryId, diary.reminderStage)
        )
    }

    fun cancel(context: Context, diaryId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(context, diaryId, 0))
    }

    private fun alarmPendingIntent(context: Context, diaryId: Int, stage: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderIntents.EXTRA_DIARY_ID, diaryId)
            putExtra(ReminderIntents.EXTRA_STAGE, stage)
        }
        return PendingIntent.getBroadcast(
            context,
            diaryId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
