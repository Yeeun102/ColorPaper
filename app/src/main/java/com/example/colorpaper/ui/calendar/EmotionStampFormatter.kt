package com.example.colorpaper.ui.calendar

object EmotionStampFormatter {
    private val emojiByEmotion = mapOf(
        "기뻐요" to "😊", "신나요" to "🤩", "만족해요" to "😌",
        "편안해요" to "😊", "짜증나요" to "😒", "힘들어요" to "😫",
        "화나요" to "😡", "졸려요" to "😴", "우울해요" to "😔",
        "속상해요" to "😞", "불안해요" to "😰", "슬퍼요" to "😢"
    )

    fun format(rawEmotions: List<String>, maxCount: Int = 3): String = rawEmotions
        .flatMap { it.split(",") }
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinct()
        .take(maxCount)
        .joinToString("") { emotion ->
            emojiByEmotion[emotion] ?: emotion.takeIf { value ->
                value.any { character -> Character.isSurrogate(character) }
            }.orEmpty()
        }
}
