package kr.roadcanvas.app.map

/**
 * 경로 위에 표시할 현재 위치와 진행 방향. 출발점 마커와 구분되는 위치 표시로 그린다.
 * [bearingDegrees]는 북쪽 0도에서 시계 방향 각도이며 아직 방향을 모르면 null이다.
 */
data class CurrentPosition(val point: RoutePoint, val bearingDegrees: Double?)

/** 경로 지도 표시 상태 중 현재 위치를 함께 그릴 수 있는 상태. 지도가 보이고 경로·위치를 그리거나 둘러보는 때다. */
internal val RouteMapState.showsCurrentPosition: Boolean
    get() = this is RouteMapState.Route ||
        this is RouteMapState.NotEnoughPoints ||
        this is RouteMapState.Explore
