package kr.roadcanvas.app.mydrawings

import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyDrawingsUiStateTest {
    @Test
    fun `최고 점수가 같으면 최근 기록을 고른다`() {
        val items = listOf(summary(3, 80), summary(2, 90), summary(1, 90))

        assertEquals(2L, bestOf(items)?.id)
        assertNull(bestOf(emptyList()))
    }

    @Test
    fun `삭제 후에는 다음 기록을, 마지막이었다면 앞 기록을 선택한다`() {
        val items = listOf(summary(3, 0), summary(2, 0), summary(1, 0))

        assertEquals(2L, nextSelectionAfterDelete(items, deletedId = 3))
        assertEquals(1L, nextSelectionAfterDelete(items, deletedId = 2))
        assertEquals(2L, nextSelectionAfterDelete(items, deletedId = 1))
        assertNull(nextSelectionAfterDelete(listOf(summary(1, 0)), deletedId = 1))
    }

    private fun summary(id: Long, score: Int) = CompletedDrawingSummary(
        id = id,
        mode = DrawingMode.CIRCLE,
        completedAtMillis = id * 60_000,
        score = score,
        distanceMeters = 0.0,
        durationMillis = 0
    )
}
