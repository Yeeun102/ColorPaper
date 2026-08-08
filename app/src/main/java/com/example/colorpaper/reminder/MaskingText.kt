package com.example.colorpaper.reminder

data class MaskingText(
    val original: String,
    val masked: String,
    val answers: List<String>
) {
    val hasMasks: Boolean get() = answers.isNotEmpty()

    fun matches(input: String): Boolean {
        val submitted = input.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return submitted.size == answers.size &&
            submitted.zip(answers).all { (actual, expected) -> actual == expected.trim() }
    }

    companion object {
        private val rangePattern = Regex("(\\d+)-(\\d+)")

        fun create(content: String, encodedRanges: String): MaskingText {
            val ranges = rangePattern.findAll(encodedRanges)
                .mapNotNull { match ->
                    val start = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val end = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    if (start in 0 until end && end <= content.length) start to end else null
                }
                .sortedBy { it.first }
                .fold(mutableListOf<Pair<Int, Int>>()) { merged, range ->
                    val previous = merged.lastOrNull()
                    if (previous != null && range.first <= previous.second) {
                        merged[merged.lastIndex] = previous.first to maxOf(previous.second, range.second)
                    } else {
                        merged += range
                    }
                    merged
                }
            if (ranges.isEmpty()) return MaskingText(content, content, emptyList())

            val answers = ranges.map { (start, end) -> content.substring(start, end) }
            val maskedBuilder = StringBuilder(content)
            ranges.asReversed().forEach { (start, end) ->
                maskedBuilder.replace(start, end, "____")
            }
            return MaskingText(content, maskedBuilder.toString(), answers)
        }
    }
}
