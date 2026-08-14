package com.example.colorpaper.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.colorpaper.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                AppDatabase.getDatabase(appContext).diaryDao()
                    .getReminderEnabledDiaries()
                    .filter { it.userId == userId }
                    .forEach { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
