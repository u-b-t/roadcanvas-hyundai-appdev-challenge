package kr.roadcanvas.app.modeselect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSelectColorsTest {
    private val itemBackground = MODE_ITEM_COLOR.compositeOver(COVER_DARK_COLOR)

    @Test
    fun `커버의 어두운 영역은 어두운 색이다`() {
        assertTrue(COVER_DARK_COLOR.luminance() < 0.05f)
    }

    @Test
    fun `어두운 배경 위 제목과 설명은 본문 대비 이상이다`() {
        assertContrastAtLeast(4.5f, ON_COVER_TEXT_COLOR, COVER_DARK_COLOR)
        assertContrastAtLeast(4.5f, ON_COVER_TEXT_COLOR, itemBackground)
        assertContrastAtLeast(4.5f, ON_COVER_SECONDARY_TEXT_COLOR, itemBackground)
    }

    @Test
    fun `모드 항목은 어두운 배경과 구분된다`() {
        assertTrue(itemBackground.luminance() > COVER_DARK_COLOR.luminance())
    }

    @Test
    fun `모드 카드의 기록 요약은 본문 대비 이상이고 모드 그림은 그림 배경에서 보인다`() {
        assertContrastAtLeast(4.5f, ON_COVER_SUMMARY_COLOR, itemBackground)
        val pictureBackground = MODE_ITEM_COLOR.compositeOver(itemBackground)
        assertContrastAtLeast(3f, ON_COVER_SUMMARY_COLOR, pictureBackground)
    }

    @Test
    fun `자유그림 항목의 화살표는 항목 배경에서 보인다`() {
        assertContrastAtLeast(3f, ON_COVER_SECONDARY_TEXT_COLOR, itemBackground)
    }

    @Test
    fun `내 그림 버튼 테두리는 커버 위에서 보이고 흰 글자는 본문 대비 이상이다`() {
        assertContrastAtLeast(
            3f,
            SECONDARY_ON_COVER_BORDER_COLOR.compositeOver(COVER_DARK_COLOR),
            COVER_DARK_COLOR
        )
        assertContrastAtLeast(4.5f, ON_COVER_TEXT_COLOR, COVER_DARK_COLOR)
    }

    private fun assertContrastAtLeast(minimum: Float, foreground: Color, background: Color) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("대비 $ratio < $minimum ($foreground / $background)", ratio >= minimum)
    }
}
