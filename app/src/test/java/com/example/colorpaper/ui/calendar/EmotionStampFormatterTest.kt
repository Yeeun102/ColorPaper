package com.example.colorpaper.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionStampFormatterTest {
    @Test
    fun `calendar shows distinct emotion stamps up to three`() {
        assertEquals(
            "😊😢😡",
            EmotionStampFormatter.format(listOf("기뻐요,슬퍼요", "기뻐요,화나요,졸려요"))
        )
    }
}
