package kr.roadcanvas.app.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DrawingModeTest {
    @Test
    fun `땅따먹기는 territory 키로 저장하고 점수와 이름 입력이 없다`() {
        assertEquals("territory", DrawingMode.TERRITORY.key)
        assertEquals(DrawingMode.TERRITORY, DrawingMode.fromKey("territory"))
        assertFalse(DrawingMode.TERRITORY.scored)
        assertFalse(DrawingMode.TERRITORY.nameRequired)
    }
}
