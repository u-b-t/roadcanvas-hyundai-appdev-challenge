package kr.roadcanvas.app.vehicle.simulated

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** 모의 경로 지점을 만든다. Android에 의존하지 않아 점수 계산 테스트에서도 사용할 수 있다. */
object SimulatedRouteGenerator {
    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
    private const val MIN_LOOP_POINTS = 8

    fun generate(
        type: SimulatedRouteType,
        config: SimulatedRouteConfig = SimulatedRouteConfig()
    ): List<GeoPoint> = when (type) {
        SimulatedRouteType.CIRCLE -> loop(config) { 1.0 }
        SimulatedRouteType.SQUISHED_CIRCLE -> loop(config) { angle ->
            1.0 + 0.25 * cos(2 * angle) + 0.1 * sin(3 * angle)
        }
        SimulatedRouteType.LINE -> line(config)
        SimulatedRouteType.STATIONARY -> listOf(config.center)
    }

    /** 중심에서 동쪽([eastMeters])과 북쪽([northMeters])으로 떨어진 지점 */
    fun offset(center: GeoPoint, eastMeters: Double, northMeters: Double): GeoPoint {
        val metersPerDegreeLongitude =
            METERS_PER_DEGREE_LATITUDE * cos(center.latitude * PI / 180)
        return GeoPoint(
            latitude = center.latitude + northMeters / METERS_PER_DEGREE_LATITUDE,
            longitude = center.longitude + eastMeters / metersPerDegreeLongitude
        )
    }

    /** 동쪽에서 시작해 반시계 방향으로 한 바퀴 도는 닫힌 경로. 시작점은 반복하지 않는다. */
    private fun loop(
        config: SimulatedRouteConfig,
        radiusScale: (Double) -> Double
    ): List<GeoPoint> {
        val count = max(
            MIN_LOOP_POINTS,
            (2 * PI * config.radiusMeters / config.stepMeters).roundToInt()
        )
        return List(count) { index ->
            val angle = 2 * PI * index / count
            val radius = config.radiusMeters * radiusScale(angle)
            offset(config.center, radius * cos(angle), radius * sin(angle))
        }
    }

    /** 중심을 지나는 동서 방향 지름을 서쪽 끝에서 동쪽 끝까지 갔다가 돌아오는 경로 */
    private fun line(config: SimulatedRouteConfig): List<GeoPoint> {
        val segments = max(1, (2 * config.radiusMeters / config.stepMeters).roundToInt())
        val forward = List(segments + 1) { index ->
            val east = -config.radiusMeters + 2 * config.radiusMeters * index / segments
            offset(config.center, east, 0.0)
        }
        return forward + forward.subList(1, segments).asReversed()
    }
}
