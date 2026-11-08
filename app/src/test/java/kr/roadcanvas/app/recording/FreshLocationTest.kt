package kr.roadcanvas.app.recording

import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.vehicle.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreshLocationTest {
    @Test
    fun `시작 시각 이후에 측정한 위치는 위치 시각 그대로 기록한다`() {
        val point = recordablePoint(sample(START + 1_000), START, receivedAtMillis = START + 1_500)

        assertEquals(RoutePoint(LATITUDE, LONGITUDE, START + 1_000), point)
    }

    @Test
    fun `시작 시각에 측정한 위치는 기록한다`() {
        val point = recordablePoint(sample(START), START, receivedAtMillis = START + 10)

        assertEquals(START, point?.timeMillis)
    }

    @Test
    fun `시작 시각보다 이르게 측정한 위치는 받은 시각과 상관없이 버린다`() {
        assertNull(recordablePoint(sample(START - 1), START, receivedAtMillis = START + 1_000))
        assertNull(recordablePoint(sample(START - 60_000), START, receivedAtMillis = START + 1))
    }

    @Test
    fun `위치 시각이 없으면 받은 시각으로 기록한다`() {
        val point = recordablePoint(sample(0), START, receivedAtMillis = START + 2_000)

        assertEquals(RoutePoint(LATITUDE, LONGITUDE, START + 2_000), point)
        assertEquals(START + 2_000, recordablePoint(sample(-1), START, START + 2_000)?.timeMillis)
    }

    @Test
    fun `위치 시각이 없고 받은 시각도 시작 시각보다 이르면 버린다`() {
        assertNull(recordablePoint(sample(0), START, receivedAtMillis = START - 1))
    }

    private fun sample(timeMillis: Long) = LocationSample(LATITUDE, LONGITUDE, timeMillis)

    private companion object {
        const val START = 1_700_000_000_000L
        const val LATITUDE = 37.5665
        const val LONGITUDE = 126.978
    }
}
