package kr.roadcanvas.app.ui.components

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.ui.theme.CONTROL_PANEL_WIDTH
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING
import org.junit.Assert.assertEquals
import org.junit.Test

class DriverSideOverlayPaddingTest {
    @Test
    fun `지도 콘텐츠 여백은 조작 영역이 가리는 왼쪽 띠와 그 오른쪽 간격만큼이다`() {
        val padding = driverSideOverlayContentPadding()

        assertEquals(
            SCREEN_PADDING + CONTROL_PANEL_WIDTH + SCREEN_PADDING,
            padding.calculateLeftPadding(LayoutDirection.Ltr)
        )
        assertEquals(0.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateTopPadding())
        assertEquals(0.dp, padding.calculateBottomPadding())
    }

    @Test
    fun `오른쪽에서 왼쪽으로 쓰는 언어 설정이어도 여백은 왼쪽에 준다`() {
        val padding = driverSideOverlayContentPadding(controlsWidth = 300.dp)

        assertEquals(
            SCREEN_PADDING + 300.dp + SCREEN_PADDING,
            padding.calculateLeftPadding(LayoutDirection.Rtl)
        )
        assertEquals(0.dp, padding.calculateRightPadding(LayoutDirection.Rtl))
    }
}
