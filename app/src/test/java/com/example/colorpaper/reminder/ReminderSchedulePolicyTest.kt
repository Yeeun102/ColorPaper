package com.example.colorpaper.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulePolicyTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `automatic curve uses 1 3 7 14 and 30 day stages`() {
        val anchor = 1_000_000L

        ReminderSchedulePolicy.autoCurveDays.forEachIndexed { stage, days ->
            assertEquals(
                anchor + days * day,
                ReminderSchedulePolicy.nextTriggerAt(
                    anchorAt = anchor,
                    cycleDays = ReminderSchedulePolicy.AUTO_CURVE,
                    stage = stage,
                    now = anchor
                )
            )
        }
        assertNull(
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = anchor,
                cycleDays = ReminderSchedulePolicy.AUTO_CURVE,
                stage = ReminderSchedulePolicy.autoCurveDays.size,
                now = anchor
            )
        )
    }

    @Test
    fun `custom cycle repeats from the same anchor`() {
        val anchor = 2_000_000L

        assertEquals(
            anchor + 12L * day,
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = anchor,
                cycleDays = 4,
                stage = 2,
                now = anchor
            )
        )
    }

    @Test
    fun `disabled reminder has no trigger`() {
        assertNull(
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = 1L,
                cycleDays = ReminderSchedulePolicy.DISABLED,
                stage = 0,
                now = 1L
            )
        )
    }

    @Test
    fun `overdue curve reminder is rescheduled one minute from now`() {
        val now = 10L * day
        assertEquals(
            now + 60_000L,
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = day,
                cycleDays = ReminderSchedulePolicy.AUTO_CURVE,
                stage = 0,
                now = now
            )
        )
    }

    @Test
    fun `custom pattern uses each configured elapsed day then stops`() {
        val anchor = 3_000_000L
        val pattern = "1,3,5,7"

        listOf(1, 3, 5, 7).forEachIndexed { stage, days ->
            assertEquals(
                anchor + days * day,
                ReminderSchedulePolicy.nextTriggerAt(
                    anchorAt = anchor,
                    cycleDays = ReminderSchedulePolicy.CUSTOM_PATTERN,
                    stage = stage,
                    cyclePattern = pattern,
                    now = anchor
                )
            )
        }
        assertNull(
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = anchor,
                cycleDays = ReminderSchedulePolicy.CUSTOM_PATTERN,
                stage = 4,
                cyclePattern = pattern,
                now = anchor
            )
        )
    }

    @Test
    fun `reminder does not schedule after configured end date`() {
        val calendar = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 14, 20, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        assertNull(
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = calendar.timeInMillis,
                cycleDays = 1,
                stage = 0,
                hour = 20,
                endDate = 20260814,
                now = calendar.timeInMillis
            )
        )
    }

    @Test
    fun `answering late restarts only the remaining gap to the next automatic stage`() {
        val answeredAt = 20L * day
        val adjustedAnchor = ReminderSchedulePolicy.anchorAfterAnswer(
            answeredAt = answeredAt,
            cycleDays = ReminderSchedulePolicy.AUTO_CURVE,
            answeredStage = 0
        )

        assertEquals(
            answeredAt + 2L * day,
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = adjustedAnchor,
                cycleDays = ReminderSchedulePolicy.AUTO_CURVE,
                stage = 1,
                now = answeredAt
            )
        )
    }

    @Test
    fun `answering late keeps a fixed cycle interval before the next stage`() {
        val answeredAt = 20L * day
        val adjustedAnchor = ReminderSchedulePolicy.anchorAfterAnswer(
            answeredAt = answeredAt,
            cycleDays = 4,
            answeredStage = 2
        )

        assertEquals(
            answeredAt + 4L * day,
            ReminderSchedulePolicy.nextTriggerAt(
                anchorAt = adjustedAnchor,
                cycleDays = 4,
                stage = 3,
                now = answeredAt
            )
        )
    }
}
