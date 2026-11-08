package kr.roadcanvas.app.score

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 점수 계산 상수. 근거는 docs/circle-score.md에 기록한다. */
data class CircleScoreConfig(
    val circularityWeight: Double = 0.4,
    val closureWeight: Double = 0.25,
    val rotationWeight: Double = 0.35,
    val maxRadialError: Double = 0.6,
    val closureRadiusFactor: Double = 2.0,
    val minDiameterMeters: Double = 30.0,
    val minValidPoints: Int = 10,
    val minPathLengthMeters: Double = 50.0
)

/** 위도·경도 경로가 얼마나 동그란지 0~100점으로 계산한다. */
class CircleScoreCalculator(private val config: CircleScoreConfig = CircleScoreConfig()) {

    fun calculate(points: List<GeoPoint>): CircleScoreResult {
        val valid = points.filter { it.isValid() }
        if (valid.size < config.minValidPoints) {
            return zero(ZeroScoreReason.INSUFFICIENT_POINTS)
        }
        val local = toLocalMeters(valid)
        val pathLength = local.zipWithNext { a, b -> a.distanceTo(b) }.sum()
        if (pathLength < config.minPathLengthMeters) {
            return zero(ZeroScoreReason.NO_MOVEMENT)
        }
        val circle = fitCircle(local) ?: return zero(ZeroScoreReason.NOT_CIRCULAR)
        if (2 * circle.radius < config.minDiameterMeters) {
            return zero(ZeroScoreReason.TOO_SMALL)
        }

        val radialRms = sqrt(
            local.sumOf { (it.distanceTo(circle.center) - circle.radius).squared() } / local.size
        )
        val radialRmsRatio = radialRms / circle.radius
        val circularity = max(0.0, 1 - radialRmsRatio / config.maxRadialError)

        val closureDistance = local.first().distanceTo(local.last())
        val closure =
            max(0.0, 1 - closureDistance / (config.closureRadiusFactor * circle.radius))

        val rotation = cumulativeRotation(local, circle.center)
        val rotationCompleteness = min(abs(rotation), FULL_TURN) / FULL_TURN

        val weighted = config.circularityWeight * circularity +
            config.closureWeight * closure +
            config.rotationWeight * rotationCompleteness
        return CircleScoreResult(
            score = (100 * weighted).roundToInt().coerceIn(0, 100),
            metrics = CircleScoreMetrics(
                circularity = circularity,
                closure = closure,
                rotationCompleteness = rotationCompleteness,
                radiusMeters = circle.radius,
                radialRmsRatio = radialRmsRatio,
                closureDistanceMeters = closureDistance,
                rotationRadians = rotation,
                pathLengthMeters = pathLength
            ),
            zeroReason = null
        )
    }

    private fun zero(reason: ZeroScoreReason) = CircleScoreResult(0, null, reason)

    private data class Point(val x: Double, val y: Double) {
        fun distanceTo(other: Point) = hypot(x - other.x, y - other.y)
    }

    private data class Circle(val center: Point, val radius: Double)

    private fun GeoPoint.isValid() = latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

    /** 출발점 기준 등장방형 근사로 동쪽 x, 북쪽 y(m)를 구한다. */
    private fun toLocalMeters(points: List<GeoPoint>): List<Point> {
        val origin = points.first()
        val cosLat = cos(Math.toRadians(origin.latitude))
        return points.map {
            Point(
                x = EARTH_RADIUS_METERS * Math.toRadians(it.longitude - origin.longitude) * cosLat,
                y = EARTH_RADIUS_METERS * Math.toRadians(it.latitude - origin.latitude)
            )
        }
    }

    /**
     * 대수적 최소제곱(Kåsa) 원 맞춤. 무게중심으로 옮기고 RMS 거리로 나눈 좌표에서
     * 풀어 크기와 무관하게 수치가 안정되도록 한다. 점들이 직선 위에 있으면 null이다.
     */
    private fun fitCircle(points: List<Point>): Circle? {
        val n = points.size
        val meanX = points.sumOf { it.x } / n
        val meanY = points.sumOf { it.y } / n
        val scale = sqrt(points.sumOf { (it.x - meanX).squared() + (it.y - meanY).squared() } / n)
        if (scale == 0.0) return null

        var suu = 0.0
        var svv = 0.0
        var suv = 0.0
        var suz = 0.0
        var svz = 0.0
        var sz = 0.0
        for (p in points) {
            val u = (p.x - meanX) / scale
            val v = (p.y - meanY) / scale
            val z = u * u + v * v
            suu += u * u
            svv += v * v
            suv += u * v
            suz += u * z
            svz += v * z
            sz += z
        }
        val det = suu * svv - suv * suv
        if (det / (n.toDouble() * n) < DEGENERATE_DETERMINANT) return null

        // 중심 (a, b)와 반지름 r: u² + v² + D·u + E·v + F = 0, a = -D/2, b = -E/2
        val d = -(suz * svv - svz * suv) / det
        val e = -(svz * suu - suz * suv) / det
        val f = -sz / n
        val a = -d / 2
        val b = -e / 2
        val r = sqrt(a * a + b * b - f)
        return Circle(Point(meanX + a * scale, meanY + b * scale), r * scale)
    }

    /** 중심 기준 각도 변화를 -π~π로 감아 누적한다. 반시계가 양수다. */
    private fun cumulativeRotation(points: List<Point>, center: Point): Double {
        val angles = points.map { atan2(it.y - center.y, it.x - center.x) }
        return angles.zipWithNext { a, b ->
            var delta = b - a
            if (delta > PI) delta -= FULL_TURN
            if (delta < -PI) delta += FULL_TURN
            delta
        }.sum()
    }

    private fun Double.squared() = this * this

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val FULL_TURN = 2 * PI
        const val DEGENERATE_DETERMINANT = 1e-12
    }
}
