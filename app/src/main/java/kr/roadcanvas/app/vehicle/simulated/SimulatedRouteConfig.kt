package kr.roadcanvas.app.vehicle.simulated

/**
 * 모의 경로 설정. 기본값은 서울시청 주변에서 반지름 50m, 초속 5m(시속 18km)로
 * 1초마다 위치를 하나씩 내보낸다. 원 경로는 63개 지점으로 약 63초에 한 바퀴를 돈다.
 */
data class SimulatedRouteConfig(
    val center: GeoPoint = DEFAULT_CENTER,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val speedMetersPerSecond: Double = DEFAULT_SPEED_METERS_PER_SECOND,
    val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) {
    init {
        require(radiusMeters > 0) { "반지름은 0보다 커야 합니다." }
        require(speedMetersPerSecond > 0) { "속도는 0보다 커야 합니다." }
        require(intervalMillis > 0) { "재생 간격은 0보다 커야 합니다." }
    }

    /** 재생 간격 한 번에 이동하는 거리 */
    val stepMeters: Double get() = speedMetersPerSecond * intervalMillis / 1_000.0

    companion object {
        val DEFAULT_CENTER = GeoPoint(latitude = 37.5665, longitude = 126.9780)
        const val DEFAULT_RADIUS_METERS = 50.0
        const val DEFAULT_SPEED_METERS_PER_SECOND = 5.0
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
    }
}
