package kr.roadcanvas.app.recording

import kr.roadcanvas.app.drawing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPathTest {
    @Test
    fun `위도 0_0009도 차이는 약 100m다`() {
        val distance = RoutePoint(37.5665, 126.978, 0).distanceTo(RoutePoint(37.5674, 126.978, 0))

        assertEquals(100.07, distance, 0.01)
    }

    @Test
    fun `경로 길이는 점 사이 거리의 합이다`() {
        val a = RoutePoint(37.5665, 126.978, 0)
        val b = RoutePoint(37.5674, 126.978, 1)
        val c = RoutePoint(37.5674, 126.979, 2)

        assertEquals(a.distanceTo(b) + b.distanceTo(c), pathDistanceMeters(listOf(a, b, c)), 1e-9)
    }

    @Test
    fun `점이 하나 이하면 경로 길이는 0이다`() {
        assertEquals(0.0, pathDistanceMeters(emptyList()), 0.0)
        assertEquals(0.0, pathDistanceMeters(listOf(RoutePoint(37.0, 127.0, 0))), 0.0)
    }

    @Test
    fun `받은 시각이 달라도 좌표가 같으면 같은 위치다`() {
        assertTrue(RoutePoint(37.0, 127.0, 0).isSameLocationAs(RoutePoint(37.0, 127.0, 1_000)))
        assertFalse(RoutePoint(37.0, 127.0, 0).isSameLocationAs(RoutePoint(37.0, 127.00001, 0)))
    }
}
