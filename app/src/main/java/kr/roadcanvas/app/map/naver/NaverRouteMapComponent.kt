package kr.roadcanvas.app.map.naver

import android.graphics.PointF
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
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PathOverlay
import kr.roadcanvas.app.map.CurrentPosition
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.MapInsets
import kr.roadcanvas.app.map.RouteCamera
import kr.roadcanvas.app.map.RouteCameraPadding
import kr.roadcanvas.app.map.RouteCircle
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.map.RouteMapNotice
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RouteMapState
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.map.showsCurrentPosition

/** 네이버 지도 SDK로 경로를 그린다. 키가 없거나 인증에 실패하면 지도 대신 안내를 표시한다. */
class NaverRouteMapComponent(private val auth: MapAuth) : RouteMapComponent {
    @Composable
    override fun Content(points: List<RoutePoint>, options: RouteMapOptions, modifier: Modifier) {
        val authState by auth.state.collectAsState()
        val state = RouteMapState.of(points, options, authState)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (state.showsMap) {
                NaverRouteMapView(
                    state,
                    options.contentPadding,
                    options.currentPosition,
                    Modifier.fillMaxSize()
                )
            }
            state.notice?.let {
                RouteMapNotice(it, Modifier.padding(options.contentPadding).padding(16.dp))
            }
        }
    }
}

private val RouteMapState.showsMap: Boolean
    get() = this is RouteMapState.Route ||
        this is RouteMapState.NotEnoughPoints ||
        this is RouteMapState.CurrentLocation

@Composable
private fun NaverRouteMapView(
    state: RouteMapState,
    contentPadding: PaddingValues,
    currentPosition: CurrentPosition?,
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
            val padding = MapInsets.of(contentPadding, density, layoutDirection)
            map.setContentPadding(padding.left, padding.top, padding.right, padding.bottom)
        }
    }

    // 콘텐츠 여백 안쪽에서 가장자리·지도 컨트롤·마커를 피한다. 전체 맞춤과 따라가기가 같은 여백을 쓴다.
    val cameraInsets = RouteCameraPadding.insets(density, layoutDirection)
    LaunchedEffect(naverMap, state, contentPadding, cameraInsets) {
        naverMap?.let { overlays.show(it, state, cameraInsets) }
    }
    // 카메라는 경로 표시 상태로만 맞추고, 현재 위치 표시는 경로를 다시 그린 뒤 그 위에 덧붙인다.
    val positionIconSize = with(density) { CURRENT_POSITION_ICON_SIZE.roundToPx() }
    LaunchedEffect(naverMap, state, contentPadding, currentPosition) {
        naverMap?.let {
            overlays.showCurrentPosition(it, state, currentPosition, positionIconSize)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 경로선과 출발·도착 마커, 기준 원, 현재 위치. 좌표가 바뀌면 같은 오버레이에 좌표열을 다시 지정한다. */
private class RouteOverlays {
    // 경로선(파란 굵은 선) 위에 가는 주황 테두리와 반투명 면으로 그려, 경로와 겹쳐도 원이 보이고 구분된다.
    private val circle = CircleOverlay().apply {
        color = 0x1FFB8C00
        outlineWidth = 3
        outlineColor = 0xFFFB8C00.toInt()
        globalZIndex = PathOverlay.DEFAULT_GLOBAL_Z_INDEX + 1
    }
    private val path = PathOverlay().apply {
        width = 12
        outlineWidth = 2
        color = 0xFF1E88E5.toInt()
    }
    private val startMarker = Marker().apply { captionText = "출발" }
    private val endMarker = Marker().apply { captionText = "도착" }
    private var locationOverlay: LocationOverlay? = null

    /**
     * 현재 위치나 진행 중 경로를 따라가며 이 지도에서 한 번 확대했는지. 두 방식이 같은 값을 써서
     * 처음 한 번만 확대하고, 이후에는 사용자가 바꾼 확대 수준을 유지한다.
     */
    private var followZoomed = false

    fun show(map: NaverMap, state: RouteMapState, cameraInsets: MapInsets) {
        when (state) {
            is RouteMapState.CurrentLocation -> {
                detachRoute()
                showLocation(map, state.point, cameraInsets)
            }
            is RouteMapState.Route -> {
                hideLocation()
                val coords = state.points.map { it.toLatLng() }
                path.coords = coords
                path.map = map
                startMarker.place(map, state.startMarker)
                endMarker.place(map, state.endMarker)
                circle.place(map, state.circle)
                when (state.camera) {
                    RouteCamera.FIT_BOUNDS -> with(cameraInsets) {
                        map.moveCamera(
                            CameraUpdate.fitBounds(
                                LatLngBounds.from(state.boundsPoints.map { it.toLatLng() }),
                                left,
                                top,
                                right,
                                bottom
                            )
                        )
                    }
                    RouteCamera.FOLLOW_LATEST -> follow(map, coords.last(), cameraInsets)
                }
            }
            is RouteMapState.NotEnoughPoints -> {
                // PathOverlay는 좌표가 2개 미만이면 예외가 나므로 지도에서 뗀다.
                detach()
                state.lastPoint?.let { map.moveCamera(CameraUpdate.scrollTo(it.toLatLng())) }
            }
            else -> detach()
        }
    }

    /**
     * 경로 위에 현재 위치를 진행 방향 화살표와 함께 표시한다. 출발점 마커와 구분되도록 지도의 위치 표시를 쓴다.
     * 경로를 그리지 않는 상태거나 [position]이 없으면 [show]가 정한 표시를 그대로 둔다.
     */
    fun showCurrentPosition(
        map: NaverMap,
        state: RouteMapState,
        position: CurrentPosition?,
        iconSize: Int
    ) {
        if (!state.showsCurrentPosition || position == null) return
        val overlay = map.locationOverlay
        overlay.position = position.point.toLatLng()
        // 기본 크기는 차량 화면에서 화살표가 잘 보이지 않아 키운다.
        overlay.iconWidth = iconSize
        overlay.iconHeight = iconSize
        overlay.subIconWidth = iconSize
        overlay.subIconHeight = iconSize
        val bearing = position.bearingDegrees
        overlay.subIcon = if (bearing == null) null else LocationOverlay.DEFAULT_SUB_ICON_ARROW
        overlay.bearing = bearing?.toFloat() ?: 0f
        overlay.isVisible = true
        locationOverlay = overlay
    }

    fun detach() {
        detachRoute()
        hideLocation()
    }

    private fun detachRoute() {
        path.map = null
        startMarker.map = null
        endMarker.map = null
        circle.map = null
    }

    /**
     * [position]을 [cameraInsets]를 뺀 영역 가운데에 둔다. 위치로 옮긴 뒤 화면 기준으로 밀어 회전·기울기에서도 같은 자리에 둔다.
     * 지도 SDK의 한 번의 카메라 갱신은 좌표 이동과 화면 기준 이동을 함께 적용하지 않는다.
     */
    private fun follow(map: NaverMap, position: LatLng, cameraInsets: MapInsets) {
        val scroll = if (followZoomed) {
            CameraUpdate.scrollTo(position)
        } else {
            followZoomed = true
            CameraUpdate.scrollAndZoomTo(position, FOLLOW_ZOOM)
        }
        map.moveCamera(scroll)
        val offset = RouteCameraPadding.followOffset(cameraInsets)
        map.moveCamera(CameraUpdate.scrollBy(PointF(offset.x, offset.y)))
    }

    private fun showLocation(map: NaverMap, point: RoutePoint?, cameraInsets: MapInsets) {
        val overlay = map.locationOverlay
        if (point == null) {
            overlay.isVisible = false
            return
        }
        val position = point.toLatLng()
        overlay.position = position
        overlay.iconWidth = LocationOverlay.SIZE_AUTO
        overlay.iconHeight = LocationOverlay.SIZE_AUTO
        overlay.subIcon = null
        overlay.isVisible = true
        locationOverlay = overlay
        follow(map, position, cameraInsets)
    }

    private fun hideLocation() {
        locationOverlay?.isVisible = false
        locationOverlay = null
    }

    private fun Marker.place(map: NaverMap, point: RoutePoint?) {
        if (point == null) {
            this.map = null
        } else {
            position = point.toLatLng()
            this.map = map
        }
    }

    private fun CircleOverlay.place(map: NaverMap, routeCircle: RouteCircle?) {
        if (routeCircle == null) {
            this.map = null
        } else {
            center = routeCircle.center.toLatLng()
            radius = routeCircle.radiusMeters
            this.map = map
        }
    }

    private fun RoutePoint.toLatLng() = LatLng(latitude, longitude)
}

/** 경로 위 현재 위치 표시와 진행 방향 화살표의 크기 */
private val CURRENT_POSITION_ICON_SIZE = 36.dp

/** 현재 위치나 진행 중 경로를 처음 표시할 때의 확대 수준. 반지름 수십 m의 원과 주변 도로가 보이는 정도다. */
private const val FOLLOW_ZOOM = 17.0
