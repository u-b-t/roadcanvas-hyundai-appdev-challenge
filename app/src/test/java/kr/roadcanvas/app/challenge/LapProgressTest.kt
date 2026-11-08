package kr.roadcanvas.app.challenge

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kr.roadcanvas.app.drawing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapProgressTest {
    @Test
    fun `위치가 없으면 출발점까지 거리와 진행 방향이 없다`() {
        val lap = lapProgressOf(emptyList())

        assertNull(lap.distanceToStartMeters)
        assertNull(lap.headingDegrees)
        assertEquals(0, lap.turnPercent)
        assertFalse(lap.readyToFinish)
    }

    @Test
    fun `출발점에서 북쪽으로 100m 가면 출발점까지 100m이고 진행 방향은 북쪽이다`() {
        val lap = lapProgressOf(listOf(point(0.0, 0.0), point(0.0, 50.0), point(0.0, 100.0)))

        assertEquals(100.0, lap.distanceToStartMeters!!, 0.5)
        assertEquals(0.0, lap.headingDegrees!!, 0.5)
        assertEquals(0, lap.turnPercent)
    }

    @Test
    fun `동쪽으로 가면 진행 방향은 90도다`() {
        val lap = lapProgressOf(listOf(point(0.0, 0.0), point(10.0, 0.0)))

        assertEquals(90.0, lap.headingDegrees!!, 0.5)
    }

    @Test
    fun `원을 반 바퀴 돌면 회전 진행률은 약 50%이고 출발점에서 지름만큼 떨어져 있다`() {
        val lap = lapProgressOf(circle(radius = 50.0, turns = 0.5))

        assertEquals(50.0, lap.turnPercent.toDouble(), 3.0)
        assertEquals(100.0, lap.distanceToStartMeters!!, 1.0)
        assertFalse(lap.completedLap)
        assertFalse(lap.readyToFinish)
    }

    @Test
    fun `시계 방향으로 돌아도 회전 진행률은 같다`() {
        val counter = lapProgressOf(circle(radius = 50.0, turns = 0.75))
        val clockwise = lapProgressOf(circle(radius = 50.0, turns = 0.75, clockwise = true))

        assertEquals(counter.turnPercent, clockwise.turnPercent)
        // 방위각은 시계 방향으로 늘어나므로 시계 방향 회전은 양수, 반시계 방향 회전은 음수다.
        assertTrue(clockwise.turnDegrees > 0)
        assertTrue(counter.turnDegrees < 0)
    }

    @Test
    fun `한 바퀴를 돌아 출발점 근처에 오면 종료 안내 대상이다`() {
        val lap = lapProgressOf(circle(radius = 50.0, turns = 0.97))

        assertTrue(lap.distanceToStartMeters!! <= NEAR_START_METERS)
        assertTrue(lap.completedLap)
        assertTrue(lap.readyToFinish)
    }

    @Test
    fun `한 바퀴를 넘게 돌아도 회전 진행률은 100%에서 멈춘다`() {
        val lap = lapProgressOf(circle(radius = 50.0, turns = 1.5))

        assertEquals(100, lap.turnPercent)
    }

    @Test
    fun `출발하자마자 출발점 근처에 있어도 한 바퀴를 돌지 않았으면 종료 안내 대상이 아니다`() {
        val lap = lapProgressOf(listOf(point(0.0, 0.0), point(5.0, 0.0), point(10.0, 0.0)))

        assertTrue(lap.nearStart)
        assertFalse(lap.readyToFinish)
    }

    @Test
    fun `정차 중 위치가 조금씩 흔들려도 회전으로 세지 않는다`() {
        val jitter = (0 until 40).map { i ->
            point(if (i % 2 == 0) 0.0 else 1.0, if (i % 4 < 2) 0.0 else 1.0)
        }

        val lap = lapProgressOf(listOf(point(-20.0, 0.0)) + jitter)

        assertEquals(0.0, lap.turnDegrees, 0.0)
    }

    @Test
    fun `갔다가 같은 길로 돌아오면 반 바퀴 회전이고 한 바퀴로 보지 않는다`() {
        val out = (0..20).map { point(0.0, it * 5.0) }
        val lap = lapProgressOf(out + out.reversed().drop(1))

        assertEquals(50, lap.turnPercent)
        assertTrue(lap.nearStart)
        assertFalse(lap.readyToFinish)
    }

    @Test
    fun `출발점까지 거리와 회전 진행률을 읽기 쉽게 표시한다`() {
        assertEquals("75%", formatTurnPercent(75))
        assertEquals("12 m", formatDistanceToStart(12.4))
        assertEquals("-", formatDistanceToStart(null))
    }

    /** 서울시청 기준 로컬 평면 좌표(동쪽 [east] m, 북쪽 [north] m)를 위도·경도로 바꾼다. */
    private fun point(east: Double, north: Double): RoutePoint {
        val latitude = BASE_LATITUDE + Math.toDegrees(north / EARTH_RADIUS_METERS)
        val longitude = BASE_LONGITUDE +
            Math.toDegrees(east / (EARTH_RADIUS_METERS * cos(Math.toRadians(BASE_LATITUDE))))
        return RoutePoint(latitude, longitude, 0L)
    }

    /** 출발점에서 시작해 [turns]바퀴 도는 반지름 [radius]의 원. 5m 간격이다. */
    private fun circle(
        radius: Double,
        turns: Double,
        clockwise: Boolean = false
    ): List<RoutePoint> {
        val sign = if (clockwise) -1.0 else 1.0
        val count = (2 * PI * radius * turns / 5.0).toInt()
        return (0..count).map { i ->
            val angle = sign * 2 * PI * turns * i / count
            point(radius * sin(angle), radius - radius * cos(angle))
        }
    }

    private companion object {
        const val BASE_LATITUDE = 37.5665
        const val BASE_LONGITUDE = 126.978
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
