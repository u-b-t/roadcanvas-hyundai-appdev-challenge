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
    fun `P이면 속도와 UX 규제에 관계없이 조작 가능이다`() {
        val restrictionOf = CarUxRestrictionsDrivingRestrictionProvider::drivingRestrictionOf
        for (stopped in listOf(true, false, null)) {
            for (requires in listOf(true, false, null)) {
                assertEquals(
                    DrivingRestriction.UNRESTRICTED,
                    restrictionOf(CarGear.PARK, stopped, requires)
                )
            }
        }
    }

    @Test
    fun `P가 아니면 속도로 판단하고 멈췄으면 조작 가능이다`() {
        val restrictionOf = CarUxRestrictionsDrivingRestrictionProvider::drivingRestrictionOf
        for (gear in listOf(CarGear.DRIVE, CarGear.REVERSE, CarGear.NEUTRAL, null)) {
            for (requires in listOf(true, false, null)) {
                assertEquals(DrivingRestriction.UNRESTRICTED, restrictionOf(gear, true, requires))
                assertEquals(DrivingRestriction.RESTRICTED, restrictionOf(gear, false, requires))
            }
        }
    }

    @Test
    fun `P가 아니고 속도를 모르면 주행 최적화 필요 여부로 판단한다`() {
        val restrictionOf = CarUxRestrictionsDrivingRestrictionProvider::drivingRestrictionOf
        for (gear in listOf(CarGear.DRIVE, null)) {
            assertEquals(DrivingRestriction.RESTRICTED, restrictionOf(gear, null, true))
            assertEquals(DrivingRestriction.UNRESTRICTED, restrictionOf(gear, null, false))
            assertEquals(DrivingRestriction.UNKNOWN, restrictionOf(gear, null, null))
        }
    }

    @Test
    fun `1km_h 미만은 멈춘 것으로 보고 후진 속도는 절댓값으로 본다`() {
        val isStopped = CarUxRestrictionsDrivingRestrictionProvider::isStopped
        assertTrue(isStopped(0f))
        assertTrue(isStopped(0.27f))
        assertTrue(isStopped(-0.27f))
        assertFalse(isStopped(0.28f))
        assertFalse(isStopped(-1f))
        assertFalse(isStopped(5f))
    }

    @Test
    fun `VehicleGear 값을 기어로 바꾸고 수동 단수는 D로 본다`() {
        assertEquals(CarGear.NEUTRAL, CarGear.fromVehicleGear(0x1))
        assertEquals(CarGear.REVERSE, CarGear.fromVehicleGear(0x2))
        assertEquals(CarGear.PARK, CarGear.fromVehicleGear(0x4))
        assertEquals(CarGear.DRIVE, CarGear.fromVehicleGear(0x8))
        assertEquals(CarGear.DRIVE, CarGear.fromVehicleGear(0x10))
        assertEquals(CarGear.DRIVE, CarGear.fromVehicleGear(0x1000))
        assertEquals(null, CarGear.fromVehicleGear(0))
        assertEquals(null, CarGear.fromVehicleGear(0x3))
        assertEquals(null, CarGear.fromVehicleGear(0x2000))
    }

    @Test
    fun `D에서 멈춰 주행 최적화가 필요해도 속도가 0이면 조작 가능이다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentRequiresDistractionOptimization = true
        client.currentGear = CarGear.DRIVE
        client.currentSpeedMetersPerSecond = 0f

        val provider = subscribe()

        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)
        assertEquals("기어·속도 (D, 정지)", provider.basis.value)

        client.notifySpeed(5f)
        assertEquals(DrivingRestriction.RESTRICTED, provider.restriction.value)
        assertEquals("기어·속도 (D, 이동)", provider.basis.value)

        client.notifySpeed(null)
        assertEquals(DrivingRestriction.RESTRICTED, provider.restriction.value)
        assertEquals("UX 규제", provider.basis.value)

        client.notifyGear(CarGear.PARK)
        assertEquals(DrivingRestriction.UNRESTRICTED, provider.restriction.value)
        assertEquals("기어·속도 (P)", provider.basis.value)
    }

    @Test
    fun `기어와 속도를 받을 수 없으면 UX 규제로 판단한다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentRequiresDistractionOptimization = true

        val provider = subscribe()

        assertEquals(DrivingRestriction.RESTRICTED, provider.restriction.value)
        assertEquals("UX 규제", provider.basis.value)
    }

    @Test
    fun `연결이 끊기면 받은 기어와 속도도 버리고 확인 불가가 된다`() = runTest(UnconfinedTestDispatcher()) {
        client.currentGear = CarGear.PARK
        client.currentSpeedMetersPerSecond = 0f
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
