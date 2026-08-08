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
}
