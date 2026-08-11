package com.example.colorpaper.reminder

import java.util.Calendar

object ReminderSchedulePolicy {
    const val AUTO_CURVE = -1
    const val CUSTOM_PATTERN = -2
    const val DISABLED = 0
    val autoCurveDays: List<Int> = listOf(1, 3, 7, 14, 30)

    fun nextTriggerAt(
        anchorAt: Long,
        cycleDays: Int,
        stage: Int,
        cyclePattern: String = "",
        repeatLast: Boolean = false,
        hour: Int = -1,
        minute: Int = 0,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (anchorAt <= 0L || stage < 0 || cycleDays == DISABLED) return null
        val elapsedDays = elapsedDays(cycleDays, stage, cyclePattern, repeatLast) ?: return null
        val plannedAt = if (hour in 0..23) {
            Calendar.getInstance().apply {
                timeInMillis = anchorAt
                add(Calendar.DAY_OF_YEAR, elapsedDays.toInt())
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            anchorAt + elapsedDays * DAY_MILLIS
        }
        return maxOf(plannedAt, now + MIN_RESCHEDULE_DELAY_MILLIS)
    }

    fun elapsedDays(
        cycleDays: Int,
        stage: Int,
        cyclePattern: String = "",
        repeatLast: Boolean = false
    ): Long? = when {
        cycleDays == CUSTOM_PATTERN -> patternDay(parsePattern(cyclePattern), stage, repeatLast)
        cycleDays == AUTO_CURVE -> autoCurveDays.getOrNull(stage)?.toLong()
        cycleDays > 0 -> cycleDays.toLong() * (stage + 1L)
        else -> null
    }

    fun parsePattern(value: String): List<Int> = value
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .distinct()
        .sorted()

    private fun patternDay(days: List<Int>, stage: Int, repeatLast: Boolean): Long? {
        days.getOrNull(stage)?.let { return it.toLong() }
        if (!repeatLast || days.isEmpty() || stage < days.size) return null
        val interval = if (days.size == 1) days.last() else days.last() - days[days.lastIndex - 1]
        return days.last().toLong() + interval.coerceAtLeast(1).toLong() * (stage - days.lastIndex)
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val MIN_RESCHEDULE_DELAY_MILLIS = 60L * 1000L
}
