package kr.roadcanvas.app.map.fake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.util.Locale
import kotlin.math.roundToInt
import kr.roadcanvas.app.map.CurrentPosition
import kr.roadcanvas.app.map.MapAuthState
import kr.roadcanvas.app.map.MapBounds
import kr.roadcanvas.app.map.MapCell
import kr.roadcanvas.app.map.MapCellStyle
import kr.roadcanvas.app.map.MapExplore
import kr.roadcanvas.app.map.RouteCamera
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.map.RouteMapNotice
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RouteMapState
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.map.showsCurrentPosition

/**
 * 네트워크와 지도 SDK 없이 경로 지도 표시 상태를 글로 보여주는 대체 컴포넌트.
 * 둘러보기 위치나 이동 번호가 바뀌면 카메라 이동이 끝난 것처럼 그 위치를 가운데 둔 [VISIBLE_SPAN_DEGREES] 범위를 알리고,
 * 둘러보기 중심을 글로 보여준다.
 */
class FakeRouteMapComponent : RouteMapComponent {
    @Composable
    override fun Content(points: List<RoutePoint>, options: RouteMapOptions, modifier: Modifier) {
        val state = RouteMapState.of(points, options, MapAuthState.Configured)
        val onBoundsChange by rememberUpdatedState(options.onVisibleBoundsChange)
        val explore = options.explore
        LaunchedEffect(explore) {
            if (explore != null) onBoundsChange?.invoke(boundsAround(explore))
        }
        Box(modifier = modifier) {
            Column(
                modifier = Modifier.fillMaxSize().padding(options.contentPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RouteMapNotice(describe(state))
                (state as? RouteMapState.Explore)?.let { RouteMapNotice(describeCenter(it.center)) }
                options.currentPosition?.takeIf { state.showsCurrentPosition }?.let {
                    RouteMapNotice(describe(it))
                }
            }
        }
    }

    private fun boundsAround(explore: MapExplore): MapBounds {
        val half = VISIBLE_SPAN_DEGREES / 2
        return MapBounds(
            south = explore.center.latitude - half,
            west = explore.center.longitude - half,
            north = explore.center.latitude + half,
            east = explore.center.longitude + half
        )
    }

    companion object {
        /** 둘러보기 화면 범위의 위도·경도 폭(도). 서울에서 약 3.3km × 2.6km다. */
        const val VISIBLE_SPAN_DEGREES = 0.03

        fun describe(state: RouteMapState): String = when (state) {
            is RouteMapState.Route -> {
                val camera = when (state.camera) {
                    RouteCamera.FIT_BOUNDS -> "전체 경로 보기"
                    RouteCamera.FOLLOW_LATEST -> "최신 위치 따라가기"
                }
                val circle = state.circle?.let { " · 맞춤 원 반지름 ${it.radiusMeters.roundToInt()}m" }
                val area = state.area?.let { " · 채운 영역 꼭짓점 ${it.size}개" }
                val cells = describeResultCells(state.cells)
                "대체 경로 지도 · 지점 ${state.points.size}개 · $camera" +
                    "${circle.orEmpty()}${area.orEmpty()}$cells"
            }
            is RouteMapState.Explore -> {
                val mine = state.cells.count { it.mine }
                "대체 영토 지도 · 내 칸 ${mine}개 · 다른 사용자 칸 ${state.cells.size - mine}개"
            }
            is RouteMapState.CurrentLocation -> {
                val location = if (state.point == null) "현재 위치 없음" else "현재 위치 표시"
                val mine = state.cells.count { it.mine }
                val cells = if (state.cells.isEmpty()) {
                    ""
                } else {
                    " · 내 칸 ${mine}개 · 다른 사용자 칸 ${state.cells.size - mine}개"
                }
                "대체 경로 지도 · $location$cells"
            }
            else -> state.notice.orEmpty()
        }

        /**
         * 결과 지도의 칸 구분. 칸이 없으면 빈 문자열이다. 예:
         * ` · 차지한 칸 3개 · 빼앗은 칸 2개 · 이미 내 칸 1개 · 주변 내 칸 0개 · 주변 다른 사용자 칸 3개 · 칸 글자 드라이버-other`
         */
        fun describeResultCells(cells: List<MapCell>): String {
            if (cells.isEmpty()) return ""
            fun count(style: MapCellStyle, mine: Boolean? = null) =
                cells.count { it.style == style && (mine == null || it.mine == mine) }
            val labels = cells.mapNotNull { it.label }
            return " · 차지한 칸 ${count(MapCellStyle.CAPTURED)}개" +
                " · 빼앗은 칸 ${count(MapCellStyle.STOLEN)}개" +
                " · 이미 내 칸 ${count(MapCellStyle.KEPT)}개" +
                " · 주변 내 칸 ${count(MapCellStyle.FAINT, mine = true)}개" +
                " · 주변 다른 사용자 칸 ${count(MapCellStyle.FAINT, mine = false)}개" +
                if (labels.isEmpty()) "" else " · 칸 글자 ${labels.joinToString(", ")}"
        }

        /** 둘러보기 카메라 중심. 위도·경도를 소수점 다섯째 자리(약 1m)까지 보여준다. */
        fun describeCenter(center: RoutePoint): String =
            "대체 지도 중심 · %.5f, %.5f".format(Locale.ROOT, center.latitude, center.longitude)

        /** 현재 위치 표시. 진행 방향은 가장 가까운 정수 각도로 보여준다. */
        fun describe(position: CurrentPosition): String {
            val bearing = position.bearingDegrees?.let { "진행 방향 ${it.roundToInt() % 360}도" }
            return "대체 현재 위치 · ${bearing ?: "진행 방향 없음"}"
        }
    }
}
