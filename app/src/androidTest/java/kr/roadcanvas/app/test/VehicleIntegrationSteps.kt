package kr.roadcanvas.app.test

import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.di.DefaultAppContainer
import kr.roadcanvas.app.drawing.fake.fakeDrawingStorage
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.IntegrationSource
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection
import kr.roadcanvas.app.vehicle.VehiclePermissions
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import kr.roadcanvas.app.vehicle.simulated.GeoPoint
import kr.roadcanvas.app.vehicle.simulated.SimulatedLocationProvider
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class VehicleIntegrationSteps {
    private val application = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
    private val fakeContainer get() = application.container as FakeAppContainer
    private var container: AppContainer = application.container
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val receivedLocations = CopyOnWriteArrayList<LocationSample>()

    private var locationKey = IntegrationSource.SIMULATED.key
    private var drivingKey = IntegrationSource.SIMULATED.key
    private var routeType = SimulatedRouteType.CIRCLE
    private var simulatedSamples = emptyList<LocationSample>()

    @Before
    fun resetFakes() {
        fakeContainer.reset()
    }

    @After
    fun stopCollecting() {
        collectorScope.cancel()
    }

    @Given("테스트용 차량 연동이 주입되어 있다")
    fun assertFakeInjected() {
        assertEquals(IntegrationSource.TEST, application.container.vehicleIntegrationInfo.location)
        container = fakeContainer
    }

    @When("테스트에서 위치 상태를 {string}로 바꾼다")
    fun setLocationAvailability(name: String) {
        fakeContainer.locationProvider.setAvailability(locationAvailabilityOf(name))
    }

    @Then("앱이 받는 위치 상태는 {string}이다")
    fun assertLocationAvailability(name: String) {
        assertEquals(name, nameOf(container.locationProvider.availability.value))
    }

    @Then("요청할 권한에 {string}이 포함된다")
    fun assertPermissionRequested(permission: String) {
        val availability = container.locationProvider.availability.value
        assertTrue(availability is LocationAvailability.PermissionRequired)
        assertTrue(
            permission in (availability as LocationAvailability.PermissionRequired).permissions
        )
    }

    @Given("앱이 위치 수신을 시작했다")
    fun startReceivingLocations() {
        collectorScope.launch {
            container.locationProvider.locations().collect { receivedLocations += it }
        }
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                fakeContainer.locationProvider.subscriberCount.first { it > 0 }
            }
        }
    }

    @When("^테스트에서 위도 ([-\\d.]+), 경도 ([-\\d.]+) 위치를 보낸다$")
    fun sendLocation(latitude: String, longitude: String) {
        runBlocking {
            fakeContainer.locationProvider.sendLocation(
                LocationSample(
                    latitude.toDouble(),
                    longitude.toDouble(),
                    System.currentTimeMillis()
                )
            )
        }
    }

    @Then("^앱이 위도 ([-\\d.]+), 경도 ([-\\d.]+) 위치를 받는다$")
    fun assertLocationReceived(latitude: String, longitude: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (receivedLocations.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        val sample = receivedLocations.single()
        assertEquals(latitude.toDouble(), sample.latitude, 1e-9)
        assertEquals(longitude.toDouble(), sample.longitude, 1e-9)
    }

    @When("테스트에서 주행 규제 상태를 {string}로 바꾼다")
    fun setDrivingRestriction(name: String) {
        fakeContainer.drivingRestrictionProvider.setRestriction(drivingRestrictionOf(name))
    }

    @Then("앱이 받는 주행 규제 상태는 {string}이다")
    fun assertDrivingRestriction(name: String) {
        assertEquals(
            drivingRestrictionOf(name),
            container.drivingRestrictionProvider.restriction.value
        )
    }

    @Given("모의 위치 경로를 {string}로 고른다")
    fun selectRoute(key: String) {
        routeType = SimulatedRouteType.fromKey(key)
    }

    @When("모의 위치 기록을 시작해 위치 {int}개를 받는다")
    fun recordSimulatedLocations(count: Int) {
        val provider = SimulatedLocationProvider(SimulatedRouteGenerator.generate(routeType))
        simulatedSamples = runBlocking {
            withTimeout(count * 1_000L + TIMEOUT_MILLIS) {
                provider.locations().take(count).toList()
            }
        }
    }

    @Then("받은 위치는 경로의 처음 {int}개 지점이다")
    fun assertRouteStart(count: Int) {
        val expected = SimulatedRouteGenerator.generate(routeType).take(count)
        assertEquals(expected, simulatedSamples.map { it.toPoint() })
    }

    @Then("위치 사이 간격은 약 1초다")
    fun assertOneSecondInterval() {
        simulatedSamples.zipWithNext { previous, next -> next.timeMillis - previous.timeMillis }
            .forEach { gap -> assertTrue("간격 ${gap}ms", gap in 900L..2_000L) }
    }

    @Given("위치 구현으로 {string}를 고른다")
    fun selectLocationImplementation(key: String) {
        locationKey = key
    }

    @Given("주행 규제 구현으로 {string}를 고른다")
    fun selectDrivingImplementation(key: String) {
        drivingKey = key
    }

    @When("차량 연동을 구성한다")
    fun buildContainer() {
        container = DefaultAppContainer(
            VehicleIntegrationSelection.parse(locationKey, drivingKey, routeType.key),
            fakeDrawingStorage()
        )
    }

    @Then("사용 중인 구현 표시는 {string}이다")
    fun assertIntegrationLabel(label: String) {
        assertEquals(label, container.vehicleIntegrationInfo.label)
    }

    private fun LocationSample.toPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L

        val LOCATION_STATES = mapOf(
            "사용 가능" to LocationAvailability.Available,
            "미지원" to LocationAvailability.Unsupported,
            "권한 필요" to LocationAvailability.PermissionRequired(
                listOf(VehiclePermissions.FUSED_LOCATION)
            ),
            "접근 차단" to LocationAvailability.Blocked,
            "오류" to LocationAvailability.Error("테스트 오류")
        )

        val DRIVING_STATES = mapOf(
            "조작 가능" to DrivingRestriction.UNRESTRICTED,
            "조작 제한" to DrivingRestriction.RESTRICTED,
            "확인 불가" to DrivingRestriction.UNKNOWN
        )

        fun locationAvailabilityOf(name: String) =
            LOCATION_STATES[name] ?: throw IllegalArgumentException("알 수 없는 위치 상태: $name")

        fun nameOf(availability: LocationAvailability) =
            LOCATION_STATES.entries.first { it.value::class == availability::class }.key

        fun drivingRestrictionOf(name: String) =
            DRIVING_STATES[name] ?: throw IllegalArgumentException("알 수 없는 주행 규제 상태: $name")
    }
}
