package kr.roadcanvas.app.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.CONTROL_PANEL_WIDTH
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCameraPaddingTest {
    private val density = Density(2f)

    /** 1280×720dp 화면을 2배 밀도로 그린 지도(px) */
    private val mapWidth = 2560
    private val mapHeight = 1440

    private fun Int.toDp() = with(density) { toDp() }

    private fun routeArea(layoutDirection: LayoutDirection = LayoutDirection.Ltr): IntRect {
        val contentPadding = MapInsets.of(
            driverSideOverlayContentPadding(),
            density,
            layoutDirection
        )
        val cameraInsets = RouteCameraPadding.insets(density, layoutDirection)
        return (contentPadding + cameraInsets).inset(mapWidth, mapHeight)
    }

    @Test
    fun `경로 영역은 왼쪽 오버레이와 그 오른쪽 간격을 피하고 가장자리 여백을 더 둔다`() {
        val overlayRight = SCREEN_PADDING + CONTROL_PANEL_WIDTH + SCREEN_PADDING

        assertTrue(routeArea().left.toDp() >= overlayRight + RouteCameraPadding.EDGE_MARGIN)
    }

    @Test
    fun `경로 영역은 오른쪽 확대·축소 컨트롤과 가장자리 여백을 피한다`() {
        val fromRight = (mapWidth - routeArea().right).toDp()

        assertTrue(fromRight >= 46.dp + 10.dp + RouteCameraPadding.EDGE_MARGIN)
    }

    @Test
    fun `경로 영역 위쪽은 마커 높이와 가장자리 여백을, 아래쪽은 로고·축척 막대와 가장자리 여백을 비운다`() {
        val area = routeArea()

        assertEquals(
            RouteCameraPadding.EDGE_MARGIN + RouteCameraPadding.MARKER_HEIGHT,
            area.top.toDp()
        )
        assertEquals(
            RouteCameraPadding.EDGE_MARGIN + RouteCameraPadding.BOTTOM_CONTROLS_HEIGHT,
            (mapHeight - area.bottom).toDp()
        )
    }

    @Test
    fun `오른쪽에서 왼쪽으로 쓰는 언어 설정이면 확대·축소 컨트롤 여백을 왼쪽에 둔다`() {
        val insets = RouteCameraPadding.insets(density, LayoutDirection.Rtl)

        assertEquals(
            RouteCameraPadding.EDGE_MARGIN + RouteCameraPadding.ZOOM_CONTROL_WIDTH,
            insets.left.toDp()
        )
        assertEquals(RouteCameraPadding.EDGE_MARGIN, insets.right.toDp())
    }

    @Test
    fun `따라가는 지점은 여백을 뺀 영역 가운데에 놓인다`() {
        val contentPadding = MapInsets.of(
            driverSideOverlayContentPadding(),
            density,
            LayoutDirection.Ltr
        )
        val cameraInsets = RouteCameraPadding.insets(density, LayoutDirection.Ltr)
        val contentCenter = contentPadding.inset(mapWidth, mapHeight).center

        val followed = Offset(contentCenter.x.toFloat(), contentCenter.y.toFloat()) +
            RouteCameraPadding.followOffset(cameraInsets)

        val areaCenter = routeArea().center
        assertEquals(areaCenter.x.toFloat(), followed.x, 1f)
        assertEquals(areaCenter.y.toFloat(), followed.y, 1f)
    }

    @Test
    fun `콘텐츠 여백은 언어 방향에 맞춰 왼쪽·오른쪽 픽셀로 바꾼다`() {
        val insets = MapInsets.of(
            PaddingValues(start = 10.dp, top = 1.dp, end = 20.dp, bottom = 2.dp),
            density,
            LayoutDirection.Rtl
        )

        assertEquals(MapInsets(left = 40, top = 2, right = 20, bottom = 4), insets)
    }
}
