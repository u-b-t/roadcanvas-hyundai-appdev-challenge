package kr.roadcanvas.app.vehicle.pleos

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.VehiclePermissions
import kr.roadcanvas.app.vehicle.fake.FakeFusedLocationClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosLocationProviderTest {
    private val clients = mutableListOf<FakeFusedLocationClient>()
    private val warnings = CopyOnWriteArrayList<String>()
    private var supported = true
    private var permitted = true
    private var throwOnRegister = false

    private val provider by lazy {
        PleosLocationProvider(
            createClient = {
                FakeFusedLocationClient(supported).also {
                    it.throwOnRegister = throwOnRegister
                    clients += it
                }
            },
            createNoticeClient = { error("이 테스트에서는 안내를 띄우지 않는다.") },
            hasPermission = { permitted },
            logWarning = { message, _ -> warnings += message }
        )
    }

    private val sessionClient get() = clients.last()

    @Test
    fun `지원하고 권한이 있으면 초기화 후 리스너를 등록하고 멈추면 해제한다`() = runTest {
        val received = collect()

        sessionClient.sendLocation(SAMPLE)
        stop(received)

        assertEquals(listOf(SAMPLE), received.samples)
        assertEquals(LocationAvailability.Available, provider.availability.value)
        assertEquals(
            listOf("isSupported", "initialize", "register", "unregister", "release"),
            sessionClient.history
        )
        assertEquals(0, sessionClient.listenerCount)
    }

    @Test
    fun `비어 있는 위치는 전달하지 않는다`() = runTest {
        val received = collect()

        sessionClient.sendLocation(null)
        sessionClient.sendLocation(SAMPLE)
        stop(received)

        assertEquals(listOf(SAMPLE), received.samples)
    }

    @Test
    fun `수신을 다시 시작하면 해제한 인스턴스 대신 새 인스턴스를 쓴다`() = runTest {
        stop(collect())
        val first = sessionClient

        val second = collect()

        assertTrue(first.isReleased)
        assertTrue(first !== sessionClient)
        assertEquals(listOf("isSupported", "initialize", "register"), sessionClient.history)
        stop(second)
    }

    @Test
    fun `지원하지 않으면 미지원으로 알리고 초기화하지 않는다`() = runTest {
        supported = false

        assertEquals(LocationAvailability.Unsupported, provider.availability.value)
        assertEquals(emptyList<LocationSample>(), provider.locations().toList())
        assertEquals(LocationAvailability.Unsupported, provider.availability.value)
        assertEquals(listOf("isSupported"), sessionClient.history)
    }

    @Test
    fun `권한이 없으면 요청할 권한을 알리고 초기화하지 않는다`() = runTest {
        permitted = false

        assertEquals(emptyList<LocationSample>(), provider.locations().toList())
        assertEquals(PERMISSION_REQUIRED, provider.availability.value)
        assertEquals(listOf("isSupported"), sessionClient.history)
    }

    @Test
    fun `수신을 시작할 때 권한이 허용되었으면 권한 필요 상태에서 벗어난다`() = runTest {
        permitted = false
        assertEquals(PERMISSION_REQUIRED, provider.availability.value)

        permitted = true
        val received = collect()

        assertEquals(LocationAvailability.Available, provider.availability.value)
        stop(received)
    }

    @Test
    fun `등록할 때 권한 예외가 나면 권한 필요로 알리고 SDK를 해제한다`() = runTest {
        throwOnRegister = true

        assertEquals(emptyList<LocationSample>(), provider.locations().toList())
        assertEquals(PERMISSION_REQUIRED, provider.availability.value)
        assertEquals(
            listOf("isSupported", "initialize", "register", "release"),
            sessionClient.history
        )
    }

    @Test
    fun `SDK 오류는 오류 상태로 바꾸고 기록한다`() = runTest {
        val received = collect()

        sessionClient.sendError(Exception("service disconnected"))

        assertEquals(
            LocationAvailability.Error("service disconnected"),
            provider.availability.value
        )
        assertEquals(listOf("FusedLocation 오류"), warnings)
        stop(received)
    }

    @Test
    fun `오류 뒤에 위치를 받으면 다시 사용 가능 상태가 된다`() = runTest {
        val received = collect()

        sessionClient.sendError(Exception("service disconnected"))
        sessionClient.sendLocation(SAMPLE)

        assertEquals(LocationAvailability.Available, provider.availability.value)
        assertEquals(listOf(SAMPLE), received.samples)
        stop(received)
    }

    @Test
    fun `SDK 권한 오류는 권한 필요로 바꾼다`() {
        assertEquals(
            PERMISSION_REQUIRED,
            PleosLocationProvider.availabilityOf(SecurityException("need Permission"))
        )
    }

    @Test
    fun `차단을 뜻하는 SDK 오류는 접근 차단으로 바꾸고 안내를 띄울 수 있다`() = runTest {
        val received = collect()

        sessionClient.sendError(Exception("Location access BLOCKED by privacy setting"))

        assertEquals(LocationAvailability.Blocked, provider.availability.value)
        stop(received)
    }

    @Test
    fun `메시지가 없는 SDK 오류는 오류로 바꾼다`() {
        assertEquals(
            LocationAvailability.Error(null),
            PleosLocationProvider.availabilityOf(Exception())
        )
    }

    private class Received(val job: Job, val samples: List<LocationSample>)

    private fun TestScope.stop(received: Received) {
        received.job.cancel()
        advanceUntilIdle()
    }

    private fun TestScope.collect(): Received {
        val samples = CopyOnWriteArrayList<LocationSample>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.locations().collect { samples += it }
        }
        return Received(job, samples)
    }

    private companion object {
        val SAMPLE = LocationSample(37.5665, 126.978, 1_000L)
        val PERMISSION_REQUIRED =
            LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION))
    }
}
