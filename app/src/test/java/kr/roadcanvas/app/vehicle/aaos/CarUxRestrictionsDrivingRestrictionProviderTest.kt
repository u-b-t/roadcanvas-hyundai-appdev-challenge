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
