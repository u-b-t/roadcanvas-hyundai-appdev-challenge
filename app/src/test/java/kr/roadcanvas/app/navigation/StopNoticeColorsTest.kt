package kr.roadcanvas.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kr.roadcanvas.app.ui.components.COVER_BACKGROUND_COLOR
import org.junit.Assert.assertTrue
import org.junit.Test

class StopNoticeColorsTest {
    @Test
    fun `어두운 커버 위 안내 문장은 본문 대비 이상이다`() {
        assertContrastAtLeast(4.5f, STOP_NOTICE_TITLE_COLOR, COVER_BACKGROUND_COLOR)
        assertContrastAtLeast(4.5f, STOP_NOTICE_REASON_COLOR, COVER_BACKGROUND_COLOR)
    }

    @Test
    fun `경고 아이콘은 어두운 커버에서 드러난다`() {
        assertContrastAtLeast(3f, STOP_NOTICE_ICON_COLOR, COVER_BACKGROUND_COLOR)
    }

    @Test
    fun `커버 이미지는 절반 넘게 어둡게 덮는다`() {
        assertTrue(STOP_NOTICE_DIM_ALPHA in 0.5f..1f)
    }

    private fun assertContrastAtLeast(minimum: Float, foreground: Color, background: Color) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("대비 $ratio < $minimum ($foreground / $background)", ratio >= minimum)
    }
}
