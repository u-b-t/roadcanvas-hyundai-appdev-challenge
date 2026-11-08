package kr.roadcanvas.app.test

import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

private const val POLL_MILLIS = 50L

/** 시나리오 사이에 화면과 대체 구현 상태가 이어지지 않는지 확인한다. */
class TestIsolationSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private var lastScreen: TestScreen<*>? = null
    private var lastLabel = ""

    @When("테스트 화면을 닫자마자 다시 여는 것을 {int}번 반복한다")
    fun reopenRepeatedly(count: Int) {
        repeat(count) { index ->
            lastScreen?.close()
            val label = "격리 확인 화면 ${index + 1}"
            lastScreen = TestScreens.launchContent { Text(label) }
            lastLabel = label
        }
    }

    @Then("마지막에 연 테스트 화면이 {int}초 동안 재개된 상태로 표시된다")
    fun assertStaysResumed(seconds: Int) {
        val screen = requireNotNull(lastScreen)
        // Pleos 런처는 태스크가 지워지고 잠시 뒤 지도 화면을 앞으로 가져오므로 그 시간 동안 지켜본다.
        val until = SystemClock.uptimeMillis() + seconds * 1_000L
        while (SystemClock.uptimeMillis() < until) {
            assertEquals("다른 화면에 가려 일시정지되었다", Lifecycle.State.RESUMED, screen.state)
            SystemClock.sleep(POLL_MILLIS)
        }
        composeRule.onNodeWithText(lastLabel).assertIsDisplayed()
    }

    @Then("시나리오 화면이 열려 있다")
    fun assertScreenOpen() {
        assertTrue(TestScreens.hasOpenScreens)
    }

    @Then("시나리오 화면이 열려 있지 않다")
    fun assertNoScreenOpen() {
        assertFalse(TestScreens.hasOpenScreens)
    }

    @Then("기록 세션이 실행 중이지 않고 위치와 주행 규제 구독이 없다")
    fun assertNoRecording() {
        assertFalse(fakeContainer.recordingRunner.isRunning)
        assertEquals(emptyList<Any>(), fakeContainer.recordingRunner.requests.value)
        assertEquals(RecordingState.Idle, fakeContainer.challengeRecorder.state.value)
        assertEquals(0, fakeContainer.locationProvider.subscriberCount.value)
        assertEquals(0, fakeContainer.drivingRestrictionProvider.subscriberCount.value)
    }

    @Then("테스트용 저장소가 비어 있고 실패 설정이 없다")
    fun assertEmptyStorage() {
        val inProgress = fakeContainer.inProgressDrawingRepository
        val completed = fakeContainer.completedDrawingRepository
        assertNull(inProgress.failure)
        assertNull(completed.failure)
        assertEquals(StorageResult.Success(null), runBlocking { inProgress.load() })
        assertEquals(StorageResult.Success(emptyList<Any>()), runBlocking { completed.summaries() })
    }

    @Then("테스트용 위치는 사용 가능, 주행 규제는 조작 가능 상태다")
    fun assertDefaultVehicleState() {
        assertEquals(
            LocationAvailability.Available,
            fakeContainer.locationProvider.availability.value
        )
        assertEquals(
            DrivingRestriction.UNRESTRICTED,
            fakeContainer.drivingRestrictionProvider.restriction.value
        )
    }
}
