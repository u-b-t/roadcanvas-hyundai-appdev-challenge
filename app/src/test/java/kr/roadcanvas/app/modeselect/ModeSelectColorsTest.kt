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
    fun `준비 중 배지 글자는 본문 대비 이상이고 배지는 항목 배경에서 드러난다`() {
        assertContrastAtLeast(4.5f, ON_COMING_SOON_BADGE_COLOR, COMING_SOON_BADGE_COLOR)
        assertContrastAtLeast(3f, COMING_SOON_BADGE_COLOR, itemBackground)
    }

    private fun assertContrastAtLeast(minimum: Float, foreground: Color, background: Color) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("대비 $ratio < $minimum ($foreground / $background)", ratio >= minimum)
    }
}
