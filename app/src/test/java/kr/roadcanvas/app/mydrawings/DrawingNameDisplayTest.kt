package kr.roadcanvas.app.mydrawings

import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawingNameDisplayTest {
    @Test
    fun `글자 수는 앞뒤 공백을 빼고 센다`() {
        assertEquals("0/20", drawingNameCounter(""))
        assertEquals("3/20", drawingNameCounter("  한강뷰 "))
        assertEquals("21/20", drawingNameCounter("가".repeat(21)))
    }

    @Test
    fun `너무 긴 이름은 바로 오류를 보여준다`() {
        assertEquals(
            DRAWING_NAME_TOO_LONG_ERROR,
            drawingNameError("가".repeat(21), required = false, showEmptyError = false)
        )
    }

    @Test
    fun `필수 이름이 비어 있으면 저장을 시도한 뒤에만 오류를 보여준다`() {
        assertNull(drawingNameError(" ", required = true, showEmptyError = false))
        assertEquals(
            DRAWING_NAME_EMPTY_ERROR,
            drawingNameError(" ", required = true, showEmptyError = true)
        )
        assertNull(drawingNameError(" ", required = false, showEmptyError = true))
        assertNull(drawingNameError("이름", required = true, showEmptyError = true))
    }

    @Test
    fun `이름이 있으면 목록 항목에 이름과 날짜를, 없으면 날짜와 거리·소요 시간을 보여준다`() {
        val unnamed = CompletedDrawingSummary(
            id = 1,
            mode = DrawingMode.CIRCLE,
            completedAtMillis = 0,
            score = 90,
            distanceMeters = 314.0,
            durationMillis = 63_000
        )
        val date = formatDateTime(0)

        assertEquals(date to "314 m · 1분 3초", listItemTexts(unnamed))
        assertEquals("한강 하트" to date, listItemTexts(unnamed.copy(name = "한강 하트")))
    }
}
