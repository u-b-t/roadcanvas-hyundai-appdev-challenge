package kr.roadcanvas.app.map.fake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import kr.roadcanvas.app.map.CurrentPosition
import kr.roadcanvas.app.map.MapAuthState
import kr.roadcanvas.app.map.RouteCamera
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.map.RouteMapNotice
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RouteMapState
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.map.showsCurrentPosition

/** 네트워크와 지도 SDK 없이 경로 지도 표시 상태를 글로 보여주는 대체 컴포넌트 */
class FakeRouteMapComponent : RouteMapComponent {
    @Composable
    override fun Content(points: List<RoutePoint>, options: RouteMapOptions, modifier: Modifier) {
        val state = RouteMapState.of(points, options, MapAuthState.Configured)
        Box(modifier = modifier) {
            Column(
                modifier = Modifier.fillMaxSize().padding(options.contentPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RouteMapNotice(describe(state))
                options.currentPosition?.takeIf { state.showsCurrentPosition }?.let {
                    RouteMapNotice(describe(it))
                }
            }
        }
    }

    companion object {
        fun describe(state: RouteMapState): String = when (state) {
            is RouteMapState.Route -> {
                val camera = when (state.camera) {
                    RouteCamera.FIT_BOUNDS -> "전체 경로 보기"
                    RouteCamera.FOLLOW_LATEST -> "최신 위치 따라가기"
                }
                val circle = state.circle?.let { " · 맞춤 원 반지름 ${it.radiusMeters.roundToInt()}m" }
                "대체 경로 지도 · 지점 ${state.points.size}개 · $camera${circle.orEmpty()}"
            }
            is RouteMapState.CurrentLocation -> if (state.point == null) {
                "대체 경로 지도 · 현재 위치 없음"
            } else {
                "대체 경로 지도 · 현재 위치 표시"
            }
            else -> state.notice.orEmpty()
        }

        /** 현재 위치 표시. 진행 방향은 가장 가까운 정수 각도로 보여준다. */
        fun describe(position: CurrentPosition): String {
            val bearing = position.bearingDegrees?.let { "진행 방향 ${it.roundToInt() % 360}도" }
            return "대체 현재 위치 · ${bearing ?: "진행 방향 없음"}"
        }
    }
}
