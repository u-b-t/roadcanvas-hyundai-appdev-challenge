package kr.roadcanvas.app.recording

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kr.roadcanvas.app.drawing.RoutePoint

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** 두 점이 같은 좌표인지. 받은 시각은 비교하지 않는다. */
fun RoutePoint.isSameLocationAs(other: RoutePoint): Boolean =
    latitude == other.latitude && longitude == other.longitude

/** 두 점 사이의 대원 거리(m) */
fun RoutePoint.distanceTo(other: RoutePoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** 경로 길이(m). 점 사이 거리의 합이며 끊긴 구간도 직선으로 잇는다. */
fun pathDistanceMeters(path: List<RoutePoint>): Double =
    path.zipWithNext { a, b -> a.distanceTo(b) }.sum()
