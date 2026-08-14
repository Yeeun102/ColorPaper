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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderNotification {
    private const val CHANNEL_ID = "diary_reminders"

    fun show(
        context: Context,
        diary: DiaryEntity,
        stage: Int,
        questionOverride: String? = null
    ): Boolean {
        return show(
            context,
            diary,
            stage,
            notificationId(diary.diaryId, stage),
            questionOverride = questionOverride
        )
    }

    fun showTestNotifications(context: Context, diary: DiaryEntity) {
        val elapsedDays = actualElapsedDays(diary.createdAt)
        repeat(3) { stage ->
            val question = if (elapsedDays == 0L) {
                listOf(
                    "오늘의 기록을 다시 떠올려볼까요?",
                    "오늘의 나에게 어떤 말을 해주고 싶나요?",
                    "오늘 기록에서 가장 기억에 남는 건 무엇인가요?"
                )[stage]
            } else {
                ReminderMessageFactory.create(
                    diary.content, diary.emotionStamp, stage, elapsedDays
                ).title
            }
            show(
                context = context,
                diary = diary,
                stage = stage,
                id = "test:${diary.diaryId}:$stage".hashCode(),
                elapsedDaysOverride = elapsedDays,
                questionOverride = "[테스트] $question",
                openReminder = true
            )
        }
    }

    private fun show(
        context: Context,
        diary: DiaryEntity,
        stage: Int,
        id: Int,
        elapsedDaysOverride: Long? = null,
        questionOverride: String? = null,
        openReminder: Boolean = true
    ): Boolean {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val days = elapsedDaysOverride ?: ReminderSchedulePolicy.elapsedDays(
            diary.reviewCycleDays,
            stage,
            diary.reviewCyclePattern,
            diary.reviewRepeatLast
        ) ?: return false
        val question = questionOverride ?: ReminderMessageFactory.create(
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
            if (openReminder) {
                putExtra(ReminderIntents.EXTRA_DIARY_ID, diary.diaryId)
                putExtra(ReminderIntents.EXTRA_STAGE, stage)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
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
            .notify(id, notification)
        return true
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

    private fun actualElapsedDays(createdAt: String): Long = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { isLenient = false }
        val recordedAt = format.parse(createdAt)?.time ?: return@runCatching 0L
        val today = format.parse(format.format(Date()))?.time ?: return@runCatching 0L
        ((today - recordedAt) / DAY_MILLIS).coerceAtLeast(0L)
    }.getOrDefault(0L)

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
