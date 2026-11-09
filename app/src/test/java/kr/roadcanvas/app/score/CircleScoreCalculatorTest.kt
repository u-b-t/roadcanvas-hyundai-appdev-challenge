package kr.roadcanvas.app.score

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleScoreCalculatorTest {
    private val calculator = CircleScoreCalculator()

    private fun score(points: List<GeoPoint>) = calculator.calculate(points).score

    @Test
    fun `완전한 원은 크기와 관계없이 95점 이상이다`() {
        val small = calculator.calculate(TestPaths.circle(radius = 20.0))
        val large = calculator.calculate(TestPaths.circle(radius = 200.0))

        assertTrue("20m 원 ${small.score}", small.score >= 95)
        assertTrue("200m 원 ${large.score}", large.score >= 95)
        assertTrue(abs(small.score - large.score) <= 1)
        assertNull(small.zeroReason)
        assertEquals(20.0, small.metrics!!.radiusMeters, 0.1)
    }

    @Test
    fun `크기만 다른 같은 모양은 같은 점수를 받는다`() {
        assertEquals(score(TestPaths.square(40.0)), score(TestPaths.square(400.0)))
        assertEquals(score(TestPaths.ellipse(40.0, 2.0)), score(TestPaths.ellipse(400.0, 2.0)))
    }

    @Test
    fun `위치 잡음이 있는 원은 85점 이상이다`() {
        for (seed in 1..100) {
            val small = score(TestPaths.circle(radius = 20.0, noiseSigma = 2.0, seed = seed))
            val large = score(TestPaths.circle(radius = 200.0, noiseSigma = 2.0, seed = seed))
            assertTrue("seed $seed, 20m 원 $small", small >= 85)
            assertTrue("seed $seed, 200m 원 $large", large >= 85)
        }
    }

    @Test
    fun `출발점 근처에서 끝낸 반원은 한 바퀴 미완성으로 0점이다`() {
        val result = calculator.calculate(TestPaths.semicircle(20.0))

        assertEquals(0, result.score)
        assertEquals(ZeroScoreReason.INCOMPLETE_TURN, result.zeroReason)
        assertEquals(180.0, result.rotationDegrees!!, 1.0)
        assertNull(result.metrics)
    }

    @Test
    fun `끝점이 출발점에서 먼 반원은 닫히지 않아 0점이다`() {
        val result = calculator.calculate(TestPaths.semicircle(200.0))

        assertEquals(0, result.score)
        assertEquals(ZeroScoreReason.NOT_CLOSED, result.zeroReason)
        assertEquals(400.0, result.closureDistanceMeters!!, 1.0)
    }

    @Test
    fun `원 3분의 4는 한 바퀴 미완성으로 0점이다`() {
        // 끝점이 출발점에서 50m 안에 드는 크기라 닫힘은 통과하고 회전각에서 걸린다.
        for (radius in listOf(20.0, 30.0)) {
            val result = calculator.calculate(TestPaths.circle(radius, turns = 0.75))

            assertEquals(0, result.score)
            assertEquals(ZeroScoreReason.INCOMPLETE_TURN, result.zeroReason)
            assertEquals(270.0, result.rotationDegrees!!, 1.0)
        }
    }

    @Test
    fun `끝점이 출발점에서 50m보다 멀면 닫히지 않아 0점이다`() {
        val open = calculator.calculate(TestPaths.openCircle(100.0, gapMeters = 55.0))
        val closed = calculator.calculate(TestPaths.openCircle(100.0, gapMeters = 45.0))

        assertEquals(ZeroScoreReason.NOT_CLOSED, open.zeroReason)
        assertEquals(55.0, open.closureDistanceMeters!!, 1.0)
        assertNull(closed.zeroReason)
        assertTrue(closed.score > 0)
    }

    @Test
    fun `330도 이상 돌면 닫힌 원으로 보고 미만이면 한 바퀴 미완성이다`() {
        // 반지름 60m면 두 경우 모두 끝점이 출발점에서 50m 안이라 회전각만 다르다.
        val enough = calculator.calculate(TestPaths.circle(60.0, turns = 335.0 / 360))
        val short = calculator.calculate(TestPaths.circle(60.0, turns = 325.0 / 360))

        assertNull(enough.zeroReason)
        assertEquals(ZeroScoreReason.INCOMPLETE_TURN, short.zeroReason)
    }

    @Test
    fun `원을 두 바퀴 돌면 한 바퀴와 같이 95점 이상이다`() {
        val result = calculator.calculate(TestPaths.circle(20.0, turns = 2.0))

        assertTrue("두 바퀴 ${result.score}", result.score >= 95)
        assertEquals(4 * Math.PI, abs(result.metrics!!.rotationRadians), 0.01)
    }

    @Test
    fun `직선 왕복은 20점 이하다`() {
        val result = calculator.calculate(TestPaths.straightOutAndBack(100.0))

        assertTrue(result.score <= 20)
        assertEquals(ZeroScoreReason.NOT_CIRCULAR, result.zeroReason)
    }

    @Test
    fun `정사각형은 원보다 낮고 직선보다 높다`() {
        val square = score(TestPaths.square(40.0))

        assertTrue(square < score(TestPaths.circle(20.0)))
        assertTrue(square > score(TestPaths.straightOutAndBack(100.0)))
    }

    @Test
    fun `장단축비 2대1 타원은 원보다 낮다`() {
        assertTrue(score(TestPaths.ellipse(40.0, 2.0)) < score(TestPaths.circle(20.0)))
    }

    @Test
    fun `원 1점5바퀴의 회전 완성도는 1을 넘지 않는다`() {
        val metrics = calculator.calculate(TestPaths.circle(20.0, turns = 1.5)).metrics!!

        assertEquals(1.0, metrics.rotationCompleteness, 0.0)
        assertEquals(3 * Math.PI, abs(metrics.rotationRadians), 0.01)
    }

    @Test
    fun `기록이 끊겨 직선으로 이어진 원은 끊김 없는 원보다 낮지만 점수를 받는다`() {
        val gap = score(TestPaths.circleWithGap(20.0, missingFraction = 0.25))

        assertTrue(gap < score(TestPaths.circle(20.0)))
        assertTrue(gap > 0)
    }

    @Test
    fun `이동이 없으면 0점이고 사유는 이동 없음이다`() {
        val result = calculator.calculate(TestPaths.stationary(30))

        assertEquals(0, result.score)
        assertEquals(ZeroScoreReason.NO_MOVEMENT, result.zeroReason)
        assertNull(result.metrics)
    }

    @Test
    fun `점이 3개면 0점이고 사유는 위치값 부족이다`() {
        val result = calculator.calculate(TestPaths.circle(20.0).take(3))

        assertEquals(0, result.score)
        assertEquals(ZeroScoreReason.INSUFFICIENT_POINTS, result.zeroReason)
    }

    @Test
    fun `유효하지 않은 위치값은 개수에 넣지 않는다`() {
        val points = TestPaths.circle(20.0).take(9) +
            GeoPoint(Double.NaN, 127.0) +
            GeoPoint(91.0, 127.0) +
            GeoPoint(37.0, Double.POSITIVE_INFINITY)

        assertEquals(
            ZeroScoreReason.INSUFFICIENT_POINTS,
            calculator.calculate(points).zeroReason
        )
    }

    @Test
    fun `최소 크기보다 작은 원은 0점이고 사유는 최소 크기 미달이다`() {
        val result = calculator.calculate(TestPaths.circle(radius = 10.0))

        assertEquals(0, result.score)
        assertEquals(ZeroScoreReason.TOO_SMALL, result.zeroReason)
    }

    @Test
    fun `반시계 방향 원은 시계 방향 원과 같은 점수다`() {
        val counterClockwise = calculator.calculate(TestPaths.circle(20.0))
        val clockwise = calculator.calculate(TestPaths.circle(20.0, clockwise = true))

        assertEquals(clockwise.score, counterClockwise.score)
        assertTrue(counterClockwise.metrics!!.rotationRadians > 0)
        assertTrue(clockwise.metrics!!.rotationRadians < 0)
    }

    @Test
    fun `세부 지표와 점수는 정해진 범위 안에 있다`() {
        val paths = listOf(
            TestPaths.circle(20.0, noiseSigma = 2.0),
            TestPaths.openCircle(100.0, gapMeters = 45.0),
            TestPaths.square(40.0),
            TestPaths.ellipse(40.0, 2.0),
            TestPaths.circle(20.0, turns = 1.5)
        )
        for (path in paths) {
            val result = calculator.calculate(path)
            val metrics = assertNotNullMetrics(result)
            assertTrue(result.score in 0..100)
            for (value in listOf(
                metrics.circularity,
                metrics.closure,
                metrics.rotationCompleteness
            )) {
                assertTrue(value in 0.0..1.0)
            }
        }
    }

    private fun assertNotNullMetrics(result: CircleScoreResult): CircleScoreMetrics {
        assertNull(result.zeroReason)
        assertNotNull(result.metrics)
        return result.metrics!!
    }

    @Test
    fun `맞춤 원 중심은 경로가 도는 원의 중심 위경도다`() {
        // 반지름 50m 원은 출발점에서 서쪽으로 50m 떨어진 점을 중심으로 돈다.
        val metrics = calculator.calculate(TestPaths.circle(radius = 50.0)).metrics!!
        val start = TestPaths.circle(radius = 50.0).first()
        val metersPerDegreeLatitude = 6_371_000.0 * Math.PI / 180
        val metersPerDegreeLongitude =
            metersPerDegreeLatitude * kotlin.math.cos(Math.toRadians(start.latitude))

        assertEquals(0.0, (metrics.center.latitude - start.latitude) * metersPerDegreeLatitude, 0.5)
        assertEquals(
            -50.0,
            (metrics.center.longitude - start.longitude) * metersPerDegreeLongitude,
            0.5
        )
    }
}
