package com.example.colorpaper.reminder

object ReminderSchedulePolicy {
    const val AUTO_CURVE = -1
    const val DISABLED = 0
    val autoCurveDays: List<Int> = listOf(1, 3, 7, 14, 30)

    fun nextTriggerAt(
        anchorAt: Long,
        cycleDays: Int,
        stage: Int,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (anchorAt <= 0L || stage < 0 || cycleDays == DISABLED) return null
        val elapsedDays = elapsedDays(cycleDays, stage) ?: return null
        val plannedAt = anchorAt + elapsedDays * DAY_MILLIS
        return maxOf(plannedAt, now + MIN_RESCHEDULE_DELAY_MILLIS)
    }

    fun elapsedDays(cycleDays: Int, stage: Int): Long? = when {
        cycleDays == AUTO_CURVE -> autoCurveDays.getOrNull(stage)?.toLong()
        cycleDays > 0 -> cycleDays.toLong() * (stage + 1L)
        else -> null
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val MIN_RESCHEDULE_DELAY_MILLIS = 60L * 1000L
}
