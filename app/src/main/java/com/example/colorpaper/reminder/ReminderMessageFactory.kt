package com.example.colorpaper.reminder

data class ReminderMessage(val title: String)

object ReminderMessageFactory {
    private val concernKeywords = listOf("고민", "걱정", "어떻게", "어쩌지", "?")
    private val difficultEmotions = listOf("짜증나요", "힘들어요", "화나요", "우울해요", "속상해요", "불안해요", "슬퍼요")

    fun create(content: String, emotions: String?, stage: Int, elapsedDays: Long): ReminderMessage {
        val title = when {
            concernKeywords.any(content::contains) ->
                "${elapsedDays}일 전 하던 고민, 해결됐나요?"
            difficultEmotions.any { emotions.orEmpty().contains(it) } ->
                "${elapsedDays}일 전의 마음은 괜찮아졌나요?"
            else -> when (stage % 4) {
                0 -> "${elapsedDays}일 전, 이런 일이 있었어요"
                1 -> "${elapsedDays}일 전의 나를 기억하나요?"
                2 -> "그때와 지금, 무엇이 달라졌나요?"
                else -> "잠시 멈춰 그날의 기록을 돌아봐요"
            }
        }
        return ReminderMessage(title)
    }
}
