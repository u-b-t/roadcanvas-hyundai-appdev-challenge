package kr.roadcanvas.app.vehicle.simulated

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedRouteGeneratorTest {
    private val config = SimulatedRouteConfig()
    private val center = config.center

    @Test
    fun `원 경로는 기본 반지름 50m 위를 5m 간격으로 한 바퀴 돈다`() {
        val route = SimulatedRouteGenerator.generate(SimulatedRouteType.CIRCLE)

        assertEquals(63, route.size)
        route.forEach { assertEquals(50.0, distance(center, it), 0.01) }
        (route + route.first()).zipWithNext { a, b -> assertEquals(5.0, distance(a, b), 0.05) }
    }

    @Test
    fun `찌그러진 원 경로는 닫힌 경로이고 중심까지 거리가 일정하지 않다`() {
        val route = SimulatedRouteGenerator.generate(SimulatedRouteType.SQUISHED_CIRCLE)
        val distances = route.map { distance(center, it) }

        assertTrue(distances.min() < 40.0)
        assertTrue(distances.max() > 60.0)
        assertTrue(distance(route.last(), route.first()) < 10.0)
    }

    @Test
    fun `직선 왕복 경로는 동서 지름을 따라 갔다가 돌아온다`() {
        val route = SimulatedRouteGenerator.generate(SimulatedRouteType.LINE)

        assertEquals(40, route.size)
        route.forEach { assertEquals(center.latitude, it.latitude, 1e-12) }
        assertEquals(-50.0, east(route.first()), 0.01)
        assertEquals(50.0, east(route[20]), 0.01)
        assertEquals(-45.0, east(route.last()), 0.01)
        (route + route.first()).zipWithNext { a, b -> assertEquals(5.0, distance(a, b), 0.01) }
    }

    @Test
    fun `정지 경로는 중심 한 지점이다`() {
        assertEquals(
            listOf(center),
            SimulatedRouteGenerator.generate(SimulatedRouteType.STATIONARY)
        )
    }

    @Test
    fun `반지름과 속도를 바꾸면 지점 수가 바뀐다`() {
        val route = SimulatedRouteGenerator.generate(
            SimulatedRouteType.CIRCLE,
            SimulatedRouteConfig(radiusMeters = 100.0, speedMetersPerSecond = 10.0)
        )

        assertEquals(63, route.size)
        route.forEach { assertEquals(100.0, distance(center, it), 0.02) }
    }

    @Test
    fun `아주 작은 원도 최소 8개 지점을 가진다`() {
        val route = SimulatedRouteGenerator.generate(
            SimulatedRouteType.CIRCLE,
            SimulatedRouteConfig(radiusMeters = 1.0)
        )

        assertEquals(8, route.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `반지름이 0 이하이면 설정할 수 없다`() {
        SimulatedRouteConfig(radiusMeters = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `재생 간격이 0 이하이면 설정할 수 없다`() {
        SimulatedRouteConfig(intervalMillis = 0)
    }

    @Test
    fun `빌드 속성 값으로 경로 모양을 고른다`() {
        assertEquals(SimulatedRouteType.SQUISHED_CIRCLE, SimulatedRouteType.fromKey("squished"))
        assertEquals(SimulatedRouteType.LINE, SimulatedRouteType.fromKey("line"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `지원하지 않는 경로 이름은 거부한다`() {
        SimulatedRouteType.fromKey("square")
    }

    private fun east(point: GeoPoint) =
        (point.longitude - center.longitude) * METERS_PER_DEGREE * cos(center.latitude * PI / 180)

    private fun distance(a: GeoPoint, b: GeoPoint): Double {
        val north = (b.latitude - a.latitude) * METERS_PER_DEGREE
        val east = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos(a.latitude * PI / 180)
        return hypot(north, east)
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
    }
}
