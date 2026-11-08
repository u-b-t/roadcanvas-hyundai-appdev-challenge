package kr.roadcanvas.app.test

import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.VehiclePermissions
import kr.roadcanvas.app.vehicle.fake.FakeFusedLocationClient
import kr.roadcanvas.app.vehicle.pleos.PleosLocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PleosLocationSteps {
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val receivedLocations = CopyOnWriteArrayList<LocationSample>()
    private val clients = CopyOnWriteArrayList<FakeFusedLocationClient>()
    private val noticeClient = FakeFusedLocationClient()
    private var supported = true
    private var permitted = true
    private var collecting: Job? = null

    private val provider by lazy {
        PleosLocationProvider(
            createClient = { FakeFusedLocationClient(supported).also { clients += it } },
            createNoticeClient = { noticeClient },
            hasPermission = { permitted },
            logWarning = { _, _ -> }
        )
    }

    private val sessionClient get() = clients.last()

    @After
    fun stopCollecting() {
        collectorScope.cancel()
    }

    @Given("Pleos 위치 SDK가 지원되는 환경이다")
    fun sdkSupported() {
        supported = true
    }

    @Given("Pleos 위치 SDK가 지원되지 않는 환경이다")
    fun sdkUnsupported() {
        supported = false
    }

    @Given("Pleos 위치 권한이 허용되어 있다")
    fun permissionGranted() {
        permitted = true
    }

    @Given("Pleos 위치 권한이 허용되지 않았다")
    fun permissionDenied() {
        permitted = false
    }

    @When("Pleos 위치 수신을 시작한다")
    fun startReceiving() {
        val job = collectorScope.launch { provider.locations().collect { receivedLocations += it } }
        collecting = job
        waitUntil("위치 수신 시작") {
            job.isCompleted || clients.lastOrNull()?.listenerCount == 1
        }
    }

    @When("Pleos 위치 수신을 멈춘다")
    fun stopReceiving() {
        collecting?.cancel()
        waitUntil("SDK 해제") { sessionClient.isReleased }
    }

    @When("^SDK가 위도 ([-\\d.]+), 경도 ([-\\d.]+) 위치를 보낸다$")
    fun sdkSendsLocation(latitude: String, longitude: String) {
        sessionClient.sendLocation(
            LocationSample(latitude.toDouble(), longitude.toDouble(), System.currentTimeMillis())
        )
    }

    @When("SDK가 비어 있는 위치를 보낸다")
    fun sdkSendsNull() {
        sessionClient.sendLocation(null)
    }

    @When("SDK가 {string} 오류를 알린다")
    fun sdkSendsError(message: String) {
        sessionClient.sendError(Exception(message))
    }

    @When("SDK가 위치 접근 차단 오류를 알린다")
    fun sdkSendsBlockedError() {
        sessionClient.sendError(Exception("location access blocked by privacy setting"))
    }

    @When("위치 접근 차단 안내를 요청한다")
    fun requestBlockedNotice() {
        provider.showAccessBlockedNotice(ApplicationProvider.getApplicationContext())
    }

    @Then("SDK에 위치 접근 차단 안내를 요청한다")
    fun assertBlockedNoticeRequested() {
        assertEquals(listOf("showLocationAccessBlockedPopup"), noticeClient.history)
    }

    @Then("Pleos 위치 상태는 {string}이다")
    fun assertAvailability(name: String) {
        assertEquals(name, nameOf(provider.availability.value))
    }

    @Then("Pleos 위치 상태의 요청할 권한에 {string}이 포함된다")
    fun assertPermission(permission: String) {
        val availability = provider.availability.value
        assertTrue(availability is LocationAvailability.PermissionRequired)
        assertTrue(
            permission in (availability as LocationAvailability.PermissionRequired).permissions
        )
        assertEquals(VehiclePermissions.FUSED_LOCATION, permission)
    }

    @Then("^앱이 Pleos 위치 위도 ([-\\d.]+), 경도 ([-\\d.]+)을 받는다$")
    fun assertReceived(latitude: String, longitude: String) {
        waitUntil("위치 수신") { receivedLocations.isNotEmpty() }
        val sample = receivedLocations.single()
        assertEquals(latitude.toDouble(), sample.latitude, 1e-9)
        assertEquals(longitude.toDouble(), sample.longitude, 1e-9)
    }

    @Then("^앱이 받은 Pleos 위치는 위도 ([-\\d.]+), 경도 ([-\\d.]+) 하나뿐이다$")
    fun assertOnlyReceived(latitude: String, longitude: String) {
        assertReceived(latitude, longitude)
    }

    @Then("SDK 리스너를 해제한 뒤 SDK를 해제한다")
    fun assertReleasedInOrder() {
        assertEquals(listOf("unregister", "release"), sessionClient.history.takeLast(2))
        assertEquals(0, sessionClient.listenerCount)
    }

    @Then("SDK를 초기화하지 않는다")
    fun assertNotInitialized() {
        assertTrue(clients.none { "initialize" in it.history })
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "$what 대기 시간 초과" }
            Thread.sleep(20)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L

        val STATE_NAMES = mapOf(
            LocationAvailability.Available::class to "사용 가능",
            LocationAvailability.Unsupported::class to "미지원",
            LocationAvailability.PermissionRequired::class to "권한 필요",
            LocationAvailability.Blocked::class to "접근 차단",
            LocationAvailability.Error::class to "오류"
        )

        fun nameOf(availability: LocationAvailability) = STATE_NAMES.getValue(availability::class)
    }
}
