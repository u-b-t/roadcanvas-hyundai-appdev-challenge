package kr.roadcanvas.app.test

import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.aaos.AndroidCarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.aaos.CarGear
import kr.roadcanvas.app.vehicle.aaos.CarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.aaos.CarUxRestrictionsDrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.fake.FakeCarUxRestrictionsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DrivingRestrictionSteps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fakeClient = FakeCarUxRestrictionsClient()
    private var client: CarUxRestrictionsClient = fakeClient
    private var provider: CarUxRestrictionsDrivingRestrictionProvider? = null
    private var subscription: Job? = null

    @After
    fun stopSubscription() {
        scope.cancel()
    }

    @Given("Car 서비스가 주행 규제 값을 알려준다")
    fun serviceProvidesValues() {
        fakeClient.currentRequiresDistractionOptimization = false
    }

    @Given("Car 서비스의 현재 주행 최적화 필요 여부가 {string}이다")
    fun serviceHasCurrentValue(name: String) {
        fakeClient.currentRequiresDistractionOptimization = requirementOf(name)
    }

    @Given("Car 서비스가 {string}")
    fun serviceFails(situation: String) {
        when (situation) {
            "연결되지 않는다" -> fakeClient.serviceAvailable = false
            "첫 값을 주지 않는다" -> fakeClient.currentRequiresDistractionOptimization = null
            "예외를 던진다" -> fakeClient.connectFailure = IllegalStateException("테스트 예외")
            else -> throw IllegalArgumentException("알 수 없는 상황: $situation")
        }
    }

    @Given("실제 Car 서비스를 사용한다")
    fun useRealService() {
        client = AndroidCarUxRestrictionsClient(ApplicationProvider.getApplicationContext())
    }

    @Given("앱이 AAOS 주행 규제를 구독하고 있다")
    fun subscribedAlready() {
        subscribe()
        waitUntil { fakeClient.connected.value }
    }

    @When("앱이 AAOS 주행 규제를 구독한다")
    fun subscribe() {
        val newProvider = CarUxRestrictionsDrivingRestrictionProvider(client, scope)
        provider = newProvider
        subscription = newProvider.restriction.launchIn(scope)
    }

    @When("앱이 AAOS 주행 규제 구독을 멈춘다")
    fun unsubscribe() {
        subscription?.cancel()
    }

    @When("플랫폼이 주행 최적화 필요 여부를 {string}로 알린다")
    fun notifyRequirement(name: String) {
        fakeClient.notifyChanged(requirementOf(name))
    }

    @When("플랫폼이 기어 {string}와 속도 {double}km\\/h를 알린다")
    fun notifyGearAndSpeed(gear: String, kilometersPerHour: Double) {
        fakeClient.notifyGear(gearOf(gear))
        fakeClient.notifySpeed((kilometersPerHour / 3.6).toFloat())
    }

    @When("플랫폼이 기어를 {string}로 알리고 속도는 알리지 않는다")
    fun notifyGearOnly(gear: String) {
        fakeClient.notifyGear(gearOf(gear))
    }

    @When("Car 서비스 연결이 끊긴다")
    fun disconnectService() {
        fakeClient.disconnectService()
    }

    @Then("AAOS 주행 규제 상태는 {string}이다")
    fun assertRestriction(name: String) {
        val expected = DRIVING_STATES.getValue(name)
        waitUntil { currentRestriction() == expected }
        assertEquals(expected, currentRestriction())
    }

    @Then("AAOS 주행 규제 상태는 {string} 또는 {string}이다")
    fun assertRestrictionEither(first: String, second: String) {
        val expected = setOf(DRIVING_STATES.getValue(first), DRIVING_STATES.getValue(second))
        val restriction = runBlocking {
            withTimeout(REAL_SERVICE_TIMEOUT_MILLIS) {
                checkNotNull(provider).restriction.first { it in expected }
            }
        }
        assertTrue(restriction in expected)
    }

    @Then("화면 조작을 허용하지 않는다")
    fun assertInteractionNotAllowed() {
        assertFalse(currentRestriction().allowsInteraction)
    }

    @Then("Car 서비스 연결이 끊겨 있다")
    fun assertDisconnected() {
        waitUntil { !fakeClient.connected.value }
        assertFalse(fakeClient.connected.value)
    }

    private fun currentRestriction() = checkNotNull(provider).restriction.value

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L
        const val REAL_SERVICE_TIMEOUT_MILLIS = 10_000L

        val DRIVING_STATES = mapOf(
            "조작 가능" to DrivingRestriction.UNRESTRICTED,
            "조작 제한" to DrivingRestriction.RESTRICTED,
            "확인 불가" to DrivingRestriction.UNKNOWN
        )

        fun gearOf(label: String) = CarGear.entries.single { it.label == label }

        fun requirementOf(name: String) = when (name) {
            "필요" -> true
            "필요 없음" -> false
            else -> throw IllegalArgumentException("알 수 없는 필요 여부: $name")
        }
    }
}
