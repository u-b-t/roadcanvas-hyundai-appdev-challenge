package kr.roadcanvas.app.drawing

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingNameTest {
    @Test
    fun `앞뒤 공백을 제거한 이름을 받는다`() {
        assertEquals(
            DrawingNameValidation.Valid("한강 하트"),
            validateDrawingName("  한강 하트 ", required = true)
        )
    }

    @Test
    fun `필수 입력에서 비어 있거나 공백뿐이면 거부한다`() {
        assertEquals(DrawingNameValidation.Empty, validateDrawingName("", required = true))
        assertEquals(DrawingNameValidation.Empty, validateDrawingName("   \t", required = true))
    }

    @Test
    fun `선택 입력에서 비어 있거나 공백뿐이면 이름 없음으로 받는다`() {
        assertEquals(DrawingNameValidation.Valid(null), validateDrawingName("", required = false))
        assertEquals(DrawingNameValidation.Valid(null), validateDrawingName("  ", required = false))
    }

    @Test
    fun `1자와 20자는 받고 21자는 거부한다`() {
        assertEquals(DrawingNameValidation.Valid("가"), validateDrawingName("가", required = true))
        val twenty = "가".repeat(DRAWING_NAME_MAX_LENGTH)
        assertEquals(
            DrawingNameValidation.Valid(twenty),
            validateDrawingName(twenty, required = true)
        )
        assertEquals(
            DrawingNameValidation.TooLong,
            validateDrawingName(twenty + "나", required = false)
        )
    }

    @Test
    fun `글자 수는 앞뒤 공백을 빼고 센다`() {
        val twenty = "a".repeat(DRAWING_NAME_MAX_LENGTH)
        assertEquals(
            DrawingNameValidation.Valid(twenty),
            validateDrawingName("  $twenty  ", required = true)
        )
        assertEquals(DRAWING_NAME_MAX_LENGTH, normalizeDrawingName("  $twenty  ").length)
    }
}
