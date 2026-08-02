package com.example.colorpaper.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.colorpaper.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getDatabase(appContext).diaryDao()
                    .getReminderEnabledDiaries()
                    .forEach { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
