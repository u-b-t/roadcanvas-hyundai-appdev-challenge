package kr.roadcanvas.app.map.fake

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kr.roadcanvas.app.map.MapAuthState
import kr.roadcanvas.app.map.RouteCamera
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.map.RouteMapNotice
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RouteMapState
import kr.roadcanvas.app.map.RoutePoint

/** 네트워크와 지도 SDK 없이 경로 지도 표시 상태를 글로 보여주는 대체 컴포넌트 */
class FakeRouteMapComponent : RouteMapComponent {
    @Composable
    override fun Content(points: List<RoutePoint>, options: RouteMapOptions, modifier: Modifier) {
        val state = RouteMapState.of(points, options, MapAuthState.Configured)
        Box(modifier = modifier) {
            Box(
                modifier = Modifier.fillMaxSize().padding(options.contentPadding),
                contentAlignment = Alignment.Center
            ) {
                RouteMapNotice(describe(state))
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
                "대체 경로 지도 · 지점 ${state.points.size}개 · $camera"
            }
            else -> state.notice.orEmpty()
        }
    }
}
