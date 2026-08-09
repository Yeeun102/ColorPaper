package com.example.colorpaper.ui.diary

import android.view.View
import android.view.ViewGroup

/** Keeps movable diary items inside the visible paper, excluding the binding strip. */
object DiaryPageBounds {
    private const val VIEWPORT_WIDTH = 401f
    private const val VIEWPORT_HEIGHT = 510f
    private const val CONTENT_LEFT = 58f
    // Stay inside the straight edges so rectangular items cannot cross rounded corners.
    private const val CONTENT_RIGHT = 378f
    private const val CONTENT_TOP = 20f
    private const val CONTENT_BOTTOM = 482f

    fun move(view: View, diaryPage: View, dx: Float, dy: Float) {
        view.translationX += dx
        view.translationY += dy
        clamp(view, diaryPage)
    }

    fun clamp(view: View, diaryPage: View) {
        val parent = view.parent as? ViewGroup ?: return
        if (
            parent.width == 0 || parent.height == 0 ||
            view.width == 0 || view.height == 0 ||
            diaryPage.width == 0 || diaryPage.height == 0
        ) return

        val parentLocation = IntArray(2)
        val pageLocation = IntArray(2)
        parent.getLocationOnScreen(parentLocation)
        diaryPage.getLocationOnScreen(pageLocation)

        val pageXInParent = (pageLocation[0] - parentLocation[0]).toFloat()
        val pageYInParent = (pageLocation[1] - parentLocation[1]).toFloat()
        val contentLeft = pageXInParent + diaryPage.width * (CONTENT_LEFT / VIEWPORT_WIDTH)
        val contentRight = pageXInParent + diaryPage.width * (CONTENT_RIGHT / VIEWPORT_WIDTH)
        val contentTop = pageYInParent + diaryPage.height * (CONTENT_TOP / VIEWPORT_HEIGHT)
        val contentBottom = pageYInParent + diaryPage.height * (CONTENT_BOTTOM / VIEWPORT_HEIGHT)

        val minX = contentLeft - view.left
        val maxX = (contentRight - view.right).coerceAtLeast(minX)
        val minY = contentTop - view.top
        val maxY = (contentBottom - view.bottom).coerceAtLeast(minY)

        view.translationX = view.translationX.coerceIn(minX, maxX)
        view.translationY = view.translationY.coerceIn(minY, maxY)
    }
}
