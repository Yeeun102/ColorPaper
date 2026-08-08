package com.example.colorpaper.ui.profile

data class HighlightItem(
    val id: String = "",          // Firestore 문서 ID 또는 Local ID
    val diaryId: Int = 0,         // 연관된 일기 ID
    val date: String = "",        // 표시 날짜 (MM/dd 또는 yyyy.MM.dd)
    val highlightedText: String = "" // 형광펜으로 하이라이트된 텍스트 내용
)