package kr.roadcanvas.app.vehicle.aaos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.fake.FakeCarUxRestrictionsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarUxRestrictionsDrivingRestrictionProviderTest {
    private val client = FakeCarUxRestrictionsClient()

    private fun TestScope.subscribe(): CarUxRestrictionsDrivingRestrictionProvider {
        val provider = CarUxRestrictionsDrivingRestrictionProvider(client, backgroundScope)
        provider.restriction.launchIn(backgroundScope)
        provider.basis.launchIn(backgroundScope)
        return provider
    }

    @Test
    fun `등록 직후 현재 값이 조작 가능이면 조작 가능이다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentRequiresDistractionOptimization = false

        val provider = subscribe()

        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)
    }

    @Test
    fun `주행 최적화가 필요하다는 값을 받으면 조작 제한이다`() = runTest(UnconfinedTestDispatcher()) {
        val provider = subscribe()

        client.notifyChanged(requiresDistractionOptimization = true)
        assertEquals(DrivingRestriction.RESTRICTED, provider.restriction.value)

        client.notifyChanged(requiresDistractionOptimization = false)
        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)
    }

    @Test
    fun `첫 값을 받지 못하면 확인 불가다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentRequiresDistractionOptimization = null

        val provider = subscribe()

        assertTrue(client.connected.value)
        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
    }

    @Test
    fun `Car 서비스에 연결하지 못하면 확인 불가다`() = runTest(UnconfinedTestDispatcher()) {
        client.serviceAvailable = false

        val provider = subscribe()

        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
    }

    @Test
    fun `연결 중 예외가 나면 확인 불가다`() = runTest(UnconfinedTestDispatcher()) {
        client.connectFailure = IllegalStateException("Car 서비스 없음")

        val provider = subscribe()

        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
    }

    @Test
    fun `연결이 끊기면 이전 값을 버리고 확인 불가가 된다`() = runTest(UnconfinedTestDispatcher()) {
        val provider = subscribe()
        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)

        client.disconnectService()

        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
        assertFalse(provider.restriction.value.allowsInteraction)
    }

    @Test
    fun `구독자가 없으면 연결하지 않고 마지막 구독이 끝나면 연결을 끊는다`() = runTest(UnconfinedTestDispatcher()) {
        val provider = CarUxRestrictionsDrivingRestrictionProvider(client, backgroundScope)
        assertEquals(0, client.connectCount)

        val subscription = provider.restriction.launchIn(backgroundScope)
        assertTrue(client.connected.value)
        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)

        subscription.cancel()

        assertFalse(client.connected.value)
        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
    }

    @Test
    fun `주행 상태가 있으면 주행 상태로 판단하고 IDLING은 조작 가능이다`() {
        val restrictionOf = CarUxRestrictionsDrivingRestrictionProvider::drivingRestrictionOf
        for (requires in listOf(true, false, null)) {
            assertEquals(
                DrivingRestriction.UNRESTRICTED,
                restrictionOf(CarDrivingState.PARKED, requires)
            )
            assertEquals(
                DrivingRestriction.UNRESTRICTED,
                restrictionOf(CarDrivingState.IDLING, requires)
            )
            assertEquals(
                DrivingRestriction.RESTRICTED,
                restrictionOf(CarDrivingState.MOVING, requires)
            )
            assertEquals(
                DrivingRestriction.UNKNOWN,
                restrictionOf(CarDrivingState.UNKNOWN, requires)
            )
        }
    }

    @Test
    fun `주행 상태가 없으면 주행 최적화 필요 여부로 판단한다`() {
        val restrictionOf = CarUxRestrictionsDrivingRestrictionProvider::drivingRestrictionOf
        assertEquals(DrivingRestriction.RESTRICTED, restrictionOf(null, true))
        assertEquals(DrivingRestriction.UNRESTRICTED, restrictionOf(null, false))
        assertEquals(DrivingRestriction.UNKNOWN, restrictionOf(null, null))
    }

    @Test
    fun `주행 상태 값 변환은 알 수 없는 값을 UNKNOWN으로 본다`() {
        assertEquals(CarDrivingState.UNKNOWN, CarDrivingState.fromEventValue(-1))
        assertEquals(CarDrivingState.PARKED, CarDrivingState.fromEventValue(0))
        assertEquals(CarDrivingState.IDLING, CarDrivingState.fromEventValue(1))
        assertEquals(CarDrivingState.MOVING, CarDrivingState.fromEventValue(2))
        assertEquals(CarDrivingState.UNKNOWN, CarDrivingState.fromEventValue(7))
    }

    @Test
    fun `D에서 멈춰 주행 최적화가 필요해도 IDLING이면 조작 가능이다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentRequiresDistractionOptimization = true
        client.currentDrivingState = CarDrivingState.IDLING

        val provider = subscribe()

        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)
        assertEquals("주행 상태 IDLING", provider.basis.value)

        client.notifyDrivingState(CarDrivingState.MOVING)
        assertEquals(DrivingRestriction.RESTRICTED, provider.restriction.value)

        client.notifyDrivingState(CarDrivingState.UNKNOWN)
        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
    }

    @Test
    fun `주행 상태를 받을 수 없으면 UX 규제로 판단한다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentDrivingState = null
        client.currentRequiresDistractionOptimization = true

        val provider = subscribe()

        assertEquals(DrivingRestriction.RESTRICTED, provider.restriction.value)
        assertEquals("UX 규제", provider.basis.value)
    }

    @Test
    fun `연결이 끊기면 받은 주행 상태도 버리고 확인 불가가 된다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentDrivingState = CarDrivingState.PARKED
        val provider = subscribe()
        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)

        client.disconnectService()

        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
        assertEquals(null, provider.basis.value)
    }

    @Test
    fun `Car API가 없는 환경의 구현은 확인 불가를 준다`() = runTest(UnconfinedTestDispatcher()) {
        val provider =
            CarUxRestrictionsDrivingRestrictionProvider(
                UnavailableCarUxRestrictionsClient,
                backgroundScope
            )
        provider.restriction.launchIn(backgroundScope)

        assertEquals(DrivingRestriction.UNKNOWN, provider.restriction.value)
    }
}
