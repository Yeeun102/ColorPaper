package com.example.colorpaper.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.data.model.DiaryEntity

object ReminderNotification {
    private const val CHANNEL_ID = "diary_reminders"

    fun show(context: Context, diary: DiaryEntity, stage: Int) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val days = ReminderSchedulePolicy.elapsedDays(
            diary.reviewCycleDays,
            stage,
            diary.reviewCyclePattern,
            diary.reviewRepeatLast
        ) ?: return
        val question = ReminderMessageFactory.create(
            content = diary.content,
            emotions = diary.emotionStamp,
            stage = stage,
            elapsedDays = days
        ).title
        val reminderContent = diary.content.removePrefix("[DECO]:")
        val preview = MaskingText.create(reminderContent, diary.highlightRanges)
            .masked
            .take(80)
        val openHomeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderIntents.EXTRA_DIARY_ID, diary.diaryId)
            putExtra(ReminderIntents.EXTRA_STAGE, stage)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(diary.diaryId, stage),
            openHomeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_reminder)
            .setContentTitle(question)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$question\n$preview"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId(diary.diaryId, stage), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationId(diaryId: Int, stage: Int): Int =
        "$diaryId:$stage".hashCode()
}
