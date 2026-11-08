package kr.roadcanvas.app.map

/** 지도에 표시할 경로의 한 지점 */
data class RoutePoint(val latitude: Double, val longitude: Double)

/**
 * 경로 지도 표시 옵션.
 * [showEndpointMarkers]가 true이면 출발점과 도착점에 마커를 둔다. 진행 중 경로에는 도착점이 없으므로 출발점만 표시한다.
 * [inProgress]가 true이면 최신 위치를 따라가고, false이면 전체 경로가 보이게 맞춘다.
 */
data class RouteMapOptions(val showEndpointMarkers: Boolean = true, val inProgress: Boolean = false)

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

    data class Route(
        val points: List<RoutePoint>,
        val camera: RouteCamera,
        val startMarker: RoutePoint?,
        val endMarker: RoutePoint?
    ) : RouteMapState {
        override val notice: String? = null
    }

    companion object {
        /** 경로선은 좌표가 2개 이상이어야 그릴 수 있다. */
        const val MIN_ROUTE_POINTS = 2

        fun of(
            points: List<RoutePoint>,
            options: RouteMapOptions,
            authState: MapAuthState
        ): RouteMapState = when {
            authState is MapAuthState.KeyMissing -> KeyMissing
            authState is MapAuthState.Failed -> AuthFailed(authState.errorCode)
            points.size < MIN_ROUTE_POINTS -> NotEnoughPoints(points.lastOrNull())
            options.inProgress -> Route(
                points = points,
                camera = RouteCamera.FOLLOW_LATEST,
                startMarker = points.first().takeIf { options.showEndpointMarkers },
                endMarker = null
            )
            else -> Route(
                points = points,
                camera = RouteCamera.FIT_BOUNDS,
                startMarker = points.first().takeIf { options.showEndpointMarkers },
                endMarker = points.last().takeIf { options.showEndpointMarkers }
            )
        }
    }
}
