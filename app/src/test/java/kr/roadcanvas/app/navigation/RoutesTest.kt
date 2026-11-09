package kr.roadcanvas.app.navigation

import kr.roadcanvas.app.drawing.DrawingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun `모드마다 준비 화면과 결과 화면이 다르다`() {
        assertEquals(Routes.CIRCLE_READY, Routes.ready(DrawingMode.CIRCLE))
        assertEquals(Routes.FREE_READY, Routes.ready(DrawingMode.FREE_DRAWING))
        assertEquals("circleResult/1/2", Routes.result(DrawingMode.CIRCLE, 1, 2))
        assertEquals("freeResult/1/2", Routes.result(DrawingMode.FREE_DRAWING, 1, 2))
        assertEquals(Routes.CIRCLE_RESULT, Routes.resultPattern(DrawingMode.CIRCLE))
        assertEquals(Routes.FREE_RESULT, Routes.resultPattern(DrawingMode.FREE_DRAWING))
        assertEquals(Routes.TERRITORY_READY, Routes.ready(DrawingMode.TERRITORY))
        assertEquals("territoryResult/1/2", Routes.result(DrawingMode.TERRITORY, 1, 2))
        assertEquals(Routes.TERRITORY_RESULT, Routes.resultPattern(DrawingMode.TERRITORY))
    }

    @Test
    fun `진행 화면은 모든 모드가 함께 쓰고 시작한 모드를 인자로 넘긴다`() {
        assertEquals("circleProgress", Routes.progress())
        assertEquals("circleProgress?mode=free_drawing", Routes.progress(DrawingMode.FREE_DRAWING))
        assertEquals("circleProgress?mode=circle", Routes.progress(DrawingMode.CIRCLE))
    }
}
