package kr.roadcanvas.app.vehicle.simulated

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedLocationProviderTest {
    private val route = listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0))

    @Test
    fun `기록을 시작하면 경로를 1초 간격으로 재생한다`() = runTest {
        val samples = provider().locations().take(2).toList()

        assertEquals(
            listOf(LocationSample(1.0, 2.0, 0), LocationSample(3.0, 4.0, 1_000)),
            samples
        )
    }

    @Test
    fun `경로 끝에 도달하면 처음부터 다시 재생한다`() = runTest {
        val samples = provider().locations().take(5).toList()

        assertEquals(
            listOf(1.0, 3.0, 1.0, 3.0, 1.0),
            samples.map { it.latitude }
        )
        assertEquals(4_000L, samples.last().timeMillis)
    }

    @Test
    fun `기록을 다시 시작하면 경로 처음부터 재생한다`() = runTest {
        val provider = provider()
        provider.locations().take(1).toList()

        assertEquals(1.0, provider.locations().take(1).toList().single().latitude, 0.0)
    }

    @Test
    fun `모의 위치는 항상 사용할 수 있다`() {
        assertEquals(
            LocationAvailability.Available,
            SimulatedLocationProvider(route).availability.value
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `빈 경로는 재생할 수 없다`() {
        SimulatedLocationProvider(emptyList())
    }

    @Test
    fun `모의 주행 규제는 항상 조작 가능 상태다`() {
        assertEquals(
            DrivingRestriction.UNRESTRICTED,
            SimulatedDrivingRestrictionProvider().restriction.value
        )
    }

    private fun TestScope.provider() =
        SimulatedLocationProvider(route, intervalMillis = 1_000, clock = { currentTime })
}
