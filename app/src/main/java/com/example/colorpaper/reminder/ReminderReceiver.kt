package com.example.colorpaper.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.colorpaper.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val diaryId = intent.getIntExtra(ReminderIntents.EXTRA_DIARY_ID, -1)
        val stage = intent.getIntExtra(ReminderIntents.EXTRA_STAGE, -1)
        if (diaryId <= 0 || stage < 0) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(appContext).diaryDao()
                val diary = dao.getDiaryById(diaryId) ?: return@launch
                if (diary.reviewCycleDays == ReminderSchedulePolicy.DISABLED ||
                    diary.reminderStage != stage
                ) return@launch

                ReminderNotification.show(appContext, diary, stage)
                dao.markReminderTriggered(diaryId, System.currentTimeMillis(), stage + 1)
                dao.getDiaryById(diaryId)?.let { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
