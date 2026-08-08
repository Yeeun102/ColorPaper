package com.example.colorpaper.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskingTextTest {
    @Test
    fun `highlight ranges become blanks and accept comma separated answers`() {
        val masking = MaskingText.create("오늘 시험을 잘 봤다", "3-5,7-8")

        assertEquals("오늘 ____을 ____ 봤다", masking.masked)
        assertEquals(listOf("시험", "잘"), masking.answers)
        assertTrue(masking.matches("시험, 잘"))
        assertFalse(masking.matches("시험, 못"))
    }

    @Test
    fun `invalid ranges are ignored`() {
        val masking = MaskingText.create("기록", "8-20")

        assertFalse(masking.hasMasks)
        assertEquals("기록", masking.masked)
    }
}
