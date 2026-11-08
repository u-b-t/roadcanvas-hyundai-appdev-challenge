package kr.roadcanvas.app.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 지도 네 변의 여백(px) */
data class MapInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    operator fun plus(other: MapInsets) = MapInsets(
        left = left + other.left,
        top = top + other.top,
        right = right + other.right,
        bottom = bottom + other.bottom
    )

    /** [width]×[height] 영역에서 이 여백을 뺀 영역 */
    fun inset(width: Int, height: Int) = IntRect(left, top, width - right, height - bottom)

    companion object {
        fun of(padding: PaddingValues, density: Density, layoutDirection: LayoutDirection) =
            with(density) {
                MapInsets(
                    left = padding.calculateLeftPadding(layoutDirection).roundToPx(),
                    top = padding.calculateTopPadding().roundToPx(),
                    right = padding.calculateRightPadding(layoutDirection).roundToPx(),
                    bottom = padding.calculateBottomPadding().roundToPx()
                )
            }
    }
}

/**
 * 경로 전체 맞춤과 따라가기가 함께 쓰는 카메라 여백 기준.
 * 왼쪽 오버레이는 지도 콘텐츠 여백([RouteMapOptions.contentPadding])으로 지도 SDK가 먼저 빼고, 그 안쪽에서
 * 가장자리 여백, 지도 컨트롤 영역과 마커 높이를 더 비운다. 전체 맞춤은 남은 영역 안에 경로를 넣고,
 * 따라가기는 남은 영역 가운데에 최신 위치를 둔다.
 */
object RouteCameraPadding {
    /** 경로와 지도 가장자리 사이 여백 */
    val EDGE_MARGIN = 32.dp

    /** 끝쪽 세로 가운데의 확대·축소 컨트롤 폭. 네이버 지도 SDK 컨트롤 너비 46dp와 끝쪽 여백 10dp다. */
    val ZOOM_CONTROL_WIDTH = 56.dp

    /** 아래쪽 지도 로고와 축척 막대가 차지하는 높이 */
    val BOTTOM_CONTROLS_HEIGHT = 32.dp

    /** 지점 위로 올라오는 출발·도착 마커 아이콘 높이 */
    val MARKER_HEIGHT = 40.dp

    /** 지도 콘텐츠 영역 안에서 카메라가 비워 둘 여백. 확대·축소 컨트롤은 언어 방향의 끝쪽에 있다. */
    fun insets(density: Density, layoutDirection: LayoutDirection): MapInsets = with(density) {
        val edge = EDGE_MARGIN.roundToPx()
        val controlsSide = edge + ZOOM_CONTROL_WIDTH.roundToPx()
        MapInsets(
            left = if (layoutDirection == LayoutDirection.Rtl) controlsSide else edge,
            top = edge + MARKER_HEIGHT.roundToPx(),
            right = if (layoutDirection == LayoutDirection.Rtl) edge else controlsSide,
            bottom = edge + BOTTOM_CONTROLS_HEIGHT.roundToPx()
        )
    }

    /** 따라가는 지점을 지도 콘텐츠 영역 가운데에서 [insets]를 뺀 영역 가운데로 옮길 양(px). 오른쪽·아래가 양수다. */
    fun followOffset(insets: MapInsets) =
        Offset((insets.left - insets.right) / 2f, (insets.top - insets.bottom) / 2f)
}
