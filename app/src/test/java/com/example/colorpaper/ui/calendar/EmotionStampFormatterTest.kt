package com.example.colorpaper.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionStampFormatterTest {
    @Test
    fun `diary emotion names become calendar stamps`() {
        assertEquals(
            "😊😢😡",
            EmotionStampFormatter.format(
                listOf("기뻐요,슬퍼요", "기뻐요,화나요,졸려요")
            )
        )
    }

    @Test
    fun `home weekly cell can limit stamp count`() {
        assertEquals(
            "😊",
            EmotionStampFormatter.format(listOf("기뻐요,슬퍼요"), maxCount = 1)
        )
    }

    @Test
    fun `all diary emotion choices have a calendar emoji`() {
        val diaryEmotions = listOf(
            "기뻐요", "신나요", "만족해요", "편안해요",
            "짜증나요", "힘들어요", "화나요", "졸려요",
            "우울해요", "속상해요", "불안해요", "슬퍼요"
        )

        diaryEmotions.forEach { emotion ->
            org.junit.Assert.assertTrue(
                "$emotion should have an emoji",
                EmotionStampFormatter.format(listOf(emotion)).isNotBlank()
            )
        }
    }
}
