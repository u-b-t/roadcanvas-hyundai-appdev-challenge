package kr.roadcanvas.app.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/** 지도에 표시할 경로의 한 지점 */
data class RoutePoint(val latitude: Double, val longitude: Double)

/** 경로와 함께 그리는 기준 원. 동그라미 점수 계산에 쓴 맞춤 원을 보여줄 때 쓴다. */
data class RouteCircle(val center: RoutePoint, val radiusMeters: Double) {
    /** 원을 감싸는 사각형의 남서쪽과 북동쪽 꼭짓점. 카메라를 맞출 때 원 전체가 보이게 한다. */
    fun boundsCorners(): List<RoutePoint> {
        val latitudeDelta = radiusMeters / METERS_PER_DEGREE_LATITUDE
        val longitudeDelta =
            radiusMeters / (METERS_PER_DEGREE_LATITUDE * cos(center.latitude * PI / 180))
        return listOf(
            RoutePoint(center.latitude - latitudeDelta, center.longitude - longitudeDelta),
            RoutePoint(center.latitude + latitudeDelta, center.longitude + longitudeDelta)
        )
    }

    private companion object {
        const val METERS_PER_DEGREE_LATITUDE = 6_371_000.0 * PI / 180
    }
}

/**
 * 경로 지도 표시 옵션.
 * [showEndpointMarkers]가 true이면 출발점과 도착점에 마커를 둔다. 진행 중 경로에는 도착점이 없으므로 출발점만 표시한다.
 * [inProgress]가 true이면 최신 위치를 따라가고, false이면 전체 경로가 보이게 맞춘다.
 * [showCurrentLocation]이 true이면 경로선 대신 마지막 좌표를 현재 위치로 표시한다.
 * [contentPadding]은 지도 위에 올린 오버레이가 가리는 영역이다. 경로·안내와 지도 로고는 이 영역을 피해 둔다.
 * [currentPosition]을 주면 경로 위에 현재 위치와 진행 방향을 출발점 마커와 구분해 표시한다. 카메라 이동에는 영향이 없다.
 * [referenceCircle]이 있으면 경로와 구분되는 스타일로 함께 그리고, 전체 경로 보기에서 원도 화면에 들어오게 맞춘다.
 * [filledArea]가 있으면 그 꼭짓점을 이은 다각형을 반투명하게 채워 경로 아래에 그린다. 땅따먹기로 차지한 영역을 보여줄 때 쓴다.
 * 첫 점을 끝에 반복하지 않으며, 꼭짓점이 [RouteMapState.MIN_AREA_POINTS]개보다 적으면 그리지 않는다.
 * [cells]는 땅따먹기 칸처럼 여러 개를 함께 채워 그리는 다각형이다. 내 칸과 다른 사용자 칸을 다른 색으로 그린다.
 * [explore]가 있고 경로가 없으면 경로 대신 그 위치에서 지도를 둘러보는 화면으로 그린다. 카메라는 [explore]가 바뀔 때만 옮긴다.
 * 둘러보기에서도 [currentPosition]이 있으면 내 위치로 표시한다.
 * [onVisibleBoundsChange]는 카메라 이동이 끝날 때마다 [contentPadding]을 뺀 지도 화면 범위를 받는다.
 */
data class RouteMapOptions(
    val showEndpointMarkers: Boolean = true,
    val inProgress: Boolean = false,
    val showCurrentLocation: Boolean = false,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val currentPosition: CurrentPosition? = null,
    val referenceCircle: RouteCircle? = null,
    val filledArea: List<RoutePoint>? = null,
    val cells: List<MapCell> = emptyList(),
    val explore: MapExplore? = null,
    val onVisibleBoundsChange: ((MapBounds) -> Unit)? = null
)

/** 지도에 채워 그리는 칸 하나. [corners]는 첫 점을 끝에 반복하지 않는다. [mine]이면 내 칸 색으로 그린다. */
data class MapCell(val corners: List<RoutePoint>, val mine: Boolean)

/**
 * 경로 없이 [center]를 [zoom] 확대 수준으로 처음 보여주는 지도 둘러보기.
 * 같은 [center]로 카메라를 다시 옮기려면 [moveId]를 바꾼다.
 */
data class MapExplore(
    val center: RoutePoint,
    val zoom: Double = DEFAULT_ZOOM,
    val moveId: Long = 0
) {
    companion object {
        /** 서울 기준 화면 가로가 약 2km라 칸(약 120m)과 주변 도로가 함께 보이는 정도다. */
        const val DEFAULT_ZOOM = 15.5
    }
}

/** 지도 화면 범위. 남쪽 [south], 서쪽 [west], 북쪽 [north], 동쪽 [east] 경계(도)다. */
data class MapBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** 경로를 그릴 때 카메라를 맞추는 방식 */
enum class RouteCamera {
    FIT_BOUNDS,
    FOLLOW_LATEST
}

/** 좌표 목록, 표시 옵션과 지도 인증 상태로 정한 지도 영역의 표시 상태 */
sealed interface RouteMapState {
    /** 지도 대신 또는 지도 위에 표시할 안내. 경로를 정상적으로 그리면 null이다. */
    val notice: String?

    data object KeyMissing : RouteMapState {
        override val notice = "지도 키가 설정되지 않아 지도를 표시할 수 없습니다."
    }

    data class AuthFailed(val errorCode: String) : RouteMapState {
        override val notice = "지도 인증에 실패해 지도를 표시할 수 없습니다. (오류 $errorCode)"
    }

    /** 경로선을 그릴 수 없다. 지도는 표시하고 마지막 위치가 있으면 그 위치로 이동한다. */
    data class NotEnoughPoints(val lastPoint: RoutePoint?) : RouteMapState {
        override val notice = "경로를 그리려면 위치가 2개 이상 필요합니다."
    }

    /** 경로 대신 현재 위치를 표시한다. 아직 위치를 받지 못했으면 [point]가 null이다. [cells]를 함께 그린다. */
    data class CurrentLocation(val point: RoutePoint?, val cells: List<MapCell> = emptyList()) :
        RouteMapState {
        override val notice: String? = null
    }

    /** 경로 없이 [center]에서 지도를 둘러보며 [cells]를 그린다. [moveId]가 바뀌면 카메라를 다시 옮긴다. */
    data class Explore(
        val center: RoutePoint,
        val zoom: Double,
        val cells: List<MapCell>,
        val moveId: Long = 0
    ) : RouteMapState {
        override val notice: String? = null
    }

    data class Route(
        val points: List<RoutePoint>,
        val camera: RouteCamera,
        val startMarker: RoutePoint?,
        val endMarker: RoutePoint?,
        val circle: RouteCircle? = null,
        val area: List<RoutePoint>? = null,
        val cells: List<MapCell> = emptyList()
    ) : RouteMapState {
        override val notice: String? = null

        /**
         * 전체 경로 보기에서 화면에 들어와야 하는 좌표. 기준 원이 있으면 원을 감싸는 꼭짓점도 넣는다.
         * 채운 영역은 경로에서 만든 꼭짓점이라 경로 안에 들어 있다.
         */
        val boundsPoints: List<RoutePoint>
            get() = points + circle?.boundsCorners().orEmpty()
    }

    companion object {
        /** 경로선은 좌표가 2개 이상이어야 그릴 수 있다. */
        const val MIN_ROUTE_POINTS = 2

        /** 채운 다각형은 꼭짓점이 3개 이상이어야 그릴 수 있다. */
        const val MIN_AREA_POINTS = 3

        fun of(
            points: List<RoutePoint>,
            options: RouteMapOptions,
            authState: MapAuthState
        ): RouteMapState = when {
            authState is MapAuthState.KeyMissing -> KeyMissing
            authState is MapAuthState.Failed -> AuthFailed(authState.errorCode)
            options.showCurrentLocation -> CurrentLocation(
                points.lastOrNull(),
                options.drawableCells
            )
            options.explore != null && points.isEmpty() -> Explore(
                center = options.explore.center,
                zoom = options.explore.zoom,
                cells = options.drawableCells,
                moveId = options.explore.moveId
            )
            points.size < MIN_ROUTE_POINTS -> NotEnoughPoints(points.lastOrNull())
            options.inProgress -> Route(
                points = points,
                camera = RouteCamera.FOLLOW_LATEST,
                startMarker = points.first().takeIf { options.showEndpointMarkers },
                endMarker = null,
                circle = options.referenceCircle,
                area = options.drawableArea,
                cells = options.drawableCells
            )
            else -> Route(
                points = points,
                camera = RouteCamera.FIT_BOUNDS,
                startMarker = points.first().takeIf { options.showEndpointMarkers },
                endMarker = points.last().takeIf { options.showEndpointMarkers },
                circle = options.referenceCircle,
                area = options.drawableArea,
                cells = options.drawableCells
            )
        }

        private val RouteMapOptions.drawableArea: List<RoutePoint>?
            get() = filledArea?.takeIf { it.size >= MIN_AREA_POINTS }

        private val RouteMapOptions.drawableCells: List<MapCell>
            get() = cells.filter { it.corners.size >= MIN_AREA_POINTS }
    }
}
