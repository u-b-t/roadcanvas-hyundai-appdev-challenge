package kr.roadcanvas.app.vehicle.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAppContainerTest {
    private val container = FakeAppContainer()

    @Test
    fun `테스트에서 보낸 위치를 수신 중인 쪽이 받는다`() = runTest {
        val received = mutableListOf<LocationSample>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.locationProvider.locations().collect { received += it }
        }
        container.locationProvider.subscriberCount.first { it > 0 }

        container.locationProvider.sendLocation(LocationSample(1.0, 2.0, 3))

        assertEquals(listOf(LocationSample(1.0, 2.0, 3)), received)
        job.cancel()
    }

    @Test
    fun `테스트에서 상태를 바꾸고 초기화할 수 있다`() {
        container.locationProvider.setAvailability(LocationAvailability.Blocked)
        container.drivingRestrictionProvider.setRestriction(DrivingRestriction.RESTRICTED)

        assertEquals(LocationAvailability.Blocked, container.locationProvider.availability.value)
        assertEquals(
            DrivingRestriction.RESTRICTED,
            container.drivingRestrictionProvider.restriction.value
        )

        container.reset()

        assertEquals(LocationAvailability.Available, container.locationProvider.availability.value)
        assertEquals(
            DrivingRestriction.UNRESTRICTED,
            container.drivingRestrictionProvider.restriction.value
        )
    }
}
