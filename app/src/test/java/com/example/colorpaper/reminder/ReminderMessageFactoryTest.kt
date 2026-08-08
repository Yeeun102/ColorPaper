package com.example.colorpaper.reminder

import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderMessageFactoryTest {
    @Test
    fun `concern record asks whether it was resolved`() {
        val message = ReminderMessageFactory.create("요즘 진로 고민이 있다", null, 0, 3)

        assertTrue(message.title.contains("고민"))
        assertTrue(message.title.contains("해결"))
    }

    @Test
    fun `ordinary records vary by reminder stage`() {
        val first = ReminderMessageFactory.create("산책을 했다", null, 0, 1)
        val second = ReminderMessageFactory.create("산책을 했다", null, 1, 3)

        assertTrue(first.title != second.title)
    }
}
