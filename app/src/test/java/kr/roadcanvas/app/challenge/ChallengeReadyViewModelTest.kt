package kr.roadcanvas.app.challenge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.VehiclePermissions
import kr.roadcanvas.app.vehicle.fake.FakeDrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeReadyViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val locationProvider = FakeLocationProvider()
    private val drivingRestrictionProvider = FakeDrivingRestrictionProvider()
    private lateinit var viewModel: ChallengeReadyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ChallengeReadyViewModel(locationProvider, drivingRestrictionProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe(): Job = backgroundScope.launch { viewModel.status.collect {} }

    private suspend fun sendLocation() {
        locationProvider.sendLocation(LocationSample(37.5665, 126.978, 0L))
    }

    @Test
    fun `현재 위치를 받으면 위치 확인 중에서 시작 가능으로 바뀐다`() = runTest(dispatcher) {
        subscribe()
        assertEquals(ChallengeReadyStatus.LocatingPosition, viewModel.status.value)

        sendLocation()

        assertEquals(ChallengeReadyStatus.Ready, viewModel.status.value)
    }

    @Test
    fun `위치 상태와 주행 규제가 바뀌면 화면 상태도 바뀐다`() = runTest(dispatcher) {
        subscribe()
        sendLocation()

        drivingRestrictionProvider.setRestriction(DrivingRestriction.UNKNOWN)
        assertEquals(ChallengeReadyStatus.WaitingForStop, viewModel.status.value)

        locationProvider.setAvailability(LocationAvailability.Blocked)
        assertEquals(ChallengeReadyStatus.Blocked, viewModel.status.value)
    }

    @Test
    fun `구독자가 없어지면 위치와 주행 규제 구독을 해제한다`() = runTest(dispatcher) {
        val job = subscribe()
        assertEquals(1, locationProvider.subscriberCount.value)
        assertEquals(1, drivingRestrictionProvider.subscriberCount.value)

        job.cancel()

        assertEquals(0, locationProvider.subscriberCount.value)
        assertEquals(0, drivingRestrictionProvider.subscriberCount.value)
    }

    @Test
    fun `다시 시도하면 위치 수신을 처음부터 다시 요청한다`() = runTest(dispatcher) {
        subscribe()
        sendLocation()
        assertEquals(1, locationProvider.requestCount.value)

        viewModel.retry()

        assertEquals(2, locationProvider.requestCount.value)
        assertEquals(1, locationProvider.subscriberCount.value)
        assertEquals(ChallengeReadyStatus.LocatingPosition, viewModel.status.value)
    }

    @Test
    fun `다시 묻지 않음으로 거절되면 설정 안내 상태가 되고 위치를 다시 요청한다`() = runTest(dispatcher) {
        locationProvider.setAvailability(
            LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION))
        )
        subscribe()

        viewModel.onPermissionResult(granted = false, shouldShowRationale = false)

        assertEquals(
            ChallengeReadyStatus.PermissionRequired(
                listOf(VehiclePermissions.FUSED_LOCATION),
                permanentlyDenied = true
            ),
            viewModel.status.value
        )
        assertEquals(2, locationProvider.requestCount.value)
    }

    @Test
    fun `거절했지만 다시 물을 수 있으면 권한 요청 상태로 남는다`() = runTest(dispatcher) {
        locationProvider.setAvailability(
            LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION))
        )
        subscribe()

        viewModel.onPermissionResult(granted = false, shouldShowRationale = true)

        assertEquals(
            ChallengeReadyStatus.PermissionRequired(
                listOf(VehiclePermissions.FUSED_LOCATION),
                permanentlyDenied = false
            ),
            viewModel.status.value
        )
    }

    @Test
    fun `현재 위치는 받은 마지막 위치이고 다시 시도하면 지운다`() = runTest(dispatcher) {
        subscribe()
        backgroundScope.launch { viewModel.currentLocation.collect {} }
        assertNull(viewModel.currentLocation.value)

        locationProvider.sendLocation(LocationSample(37.5665, 126.978, 0L))
        locationProvider.sendLocation(LocationSample(37.5674, 126.978, 1L))

        assertEquals(LocationSample(37.5674, 126.978, 1L), viewModel.currentLocation.value)
        assertEquals(ChallengeReadyStatus.Ready, viewModel.status.value)
        assertEquals(1, locationProvider.subscriberCount.value)

        viewModel.retry()

        assertNull(viewModel.currentLocation.value)
        assertEquals(ChallengeReadyStatus.LocatingPosition, viewModel.status.value)
    }
}
