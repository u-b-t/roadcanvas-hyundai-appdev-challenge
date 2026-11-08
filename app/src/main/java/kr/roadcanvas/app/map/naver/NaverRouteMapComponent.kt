package kr.roadcanvas.app.map.naver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PathOverlay
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.RouteCamera
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.map.RouteMapNotice
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RouteMapState
import kr.roadcanvas.app.map.RoutePoint

/** 네이버 지도 SDK로 경로를 그린다. 키가 없거나 인증에 실패하면 지도 대신 안내를 표시한다. */
class NaverRouteMapComponent(private val auth: MapAuth) : RouteMapComponent {
    @Composable
    override fun Content(points: List<RoutePoint>, options: RouteMapOptions, modifier: Modifier) {
        val authState by auth.state.collectAsState()
        val state = RouteMapState.of(points, options, authState)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (state is RouteMapState.Route || state is RouteMapState.NotEnoughPoints) {
                NaverRouteMapView(state, options.contentPadding, Modifier.fillMaxSize())
            }
            state.notice?.let {
                RouteMapNotice(it, Modifier.padding(options.contentPadding).padding(16.dp))
            }
        }
    }
}

@Composable
private fun NaverRouteMapView(
    state: RouteMapState,
    contentPadding: PaddingValues,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    val overlays = remember { RouteOverlays() }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val forwarder = MapViewLifecycleForwarder(mapView, beforeDestroy = overlays::detach)
        lifecycle.addObserver(forwarder)
        mapView.getMapAsync { naverMap = it }
        onDispose {
            lifecycle.removeObserver(forwarder)
            forwarder.destroy()
            naverMap = null
        }
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // 오버레이가 가린 영역을 지도 콘텐츠 패딩으로 주면 카메라 중심과 로고·법적 고지가 그 밖으로 옮겨진다.
    LaunchedEffect(naverMap, contentPadding, density, layoutDirection) {
        naverMap?.let { map ->
            with(density) {
                map.setContentPadding(
                    contentPadding.calculateLeftPadding(layoutDirection).roundToPx(),
                    contentPadding.calculateTopPadding().roundToPx(),
                    contentPadding.calculateRightPadding(layoutDirection).roundToPx(),
                    contentPadding.calculateBottomPadding().roundToPx()
                )
            }
        }
    }

    val fitBoundsPadding = with(density) { 48.dp.roundToPx() }
    LaunchedEffect(naverMap, state, contentPadding) {
        naverMap?.let { overlays.show(it, state, fitBoundsPadding) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 경로선과 출발·도착 마커. 좌표가 바뀌면 같은 오버레이에 좌표열을 다시 지정한다. */
private class RouteOverlays {
    private val path = PathOverlay().apply {
        width = 12
        outlineWidth = 2
        color = 0xFF1E88E5.toInt()
    }
    private val startMarker = Marker().apply { captionText = "출발" }
    private val endMarker = Marker().apply { captionText = "도착" }

    fun show(map: NaverMap, state: RouteMapState, fitBoundsPadding: Int) {
        when (state) {
            is RouteMapState.Route -> {
                val coords = state.points.map { it.toLatLng() }
                path.coords = coords
                path.map = map
                startMarker.place(map, state.startMarker)
                endMarker.place(map, state.endMarker)
                val update = when (state.camera) {
                    RouteCamera.FIT_BOUNDS ->
                        CameraUpdate.fitBounds(LatLngBounds.from(coords), fitBoundsPadding)
                    RouteCamera.FOLLOW_LATEST -> CameraUpdate.scrollTo(coords.last())
                }
                map.moveCamera(update)
            }
            is RouteMapState.NotEnoughPoints -> {
                // PathOverlay는 좌표가 2개 미만이면 예외가 나므로 지도에서 뗀다.
                detach()
                state.lastPoint?.let { map.moveCamera(CameraUpdate.scrollTo(it.toLatLng())) }
            }
            else -> detach()
        }
    }

    fun detach() {
        path.map = null
        startMarker.map = null
        endMarker.map = null
    }

    private fun Marker.place(map: NaverMap, point: RoutePoint?) {
        if (point == null) {
            this.map = null
        } else {
            position = point.toLatLng()
            this.map = map
        }
    }

    private fun RoutePoint.toLatLng() = LatLng(latitude, longitude)
}
