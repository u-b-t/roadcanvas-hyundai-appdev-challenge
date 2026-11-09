package kr.roadcanvas.app.score

import kotlin.math.cos
import kotlin.math.hypot

/** 출발점 기준 지역 평면 좌표. 동쪽이 x, 북쪽이 y(m)다. */
internal data class LocalPoint(val x: Double, val y: Double) {
    fun distanceTo(other: LocalPoint) = hypot(x - other.x, y - other.y)
}

internal const val EARTH_RADIUS_METERS = 6_371_000.0

internal fun GeoPoint.isValid() = latitude.isFinite() &&
    longitude.isFinite() &&
    latitude in -90.0..90.0 &&
    longitude in -180.0..180.0

/** [origin] 기준 등장방형 근사로 동쪽 x, 북쪽 y(m)를 구한다. */
internal fun toLocalMeters(
    points: List<GeoPoint>,
    origin: GeoPoint = points.first()
): List<LocalPoint> {
    val cosLat = cos(Math.toRadians(origin.latitude))
    return points.map {
        LocalPoint(
            x = EARTH_RADIUS_METERS * Math.toRadians(it.longitude - origin.longitude) * cosLat,
            y = EARTH_RADIUS_METERS * Math.toRadians(it.latitude - origin.latitude)
        )
    }
}

/** [toLocalMeters]의 역변환. [origin]은 같은 출발점이다. */
internal fun toGeoPoint(point: LocalPoint, origin: GeoPoint): GeoPoint {
    val cosLat = cos(Math.toRadians(origin.latitude))
    return GeoPoint(
        latitude = origin.latitude + Math.toDegrees(point.y / EARTH_RADIUS_METERS),
        longitude = origin.longitude + Math.toDegrees(point.x / (EARTH_RADIUS_METERS * cosLat))
    )
}
