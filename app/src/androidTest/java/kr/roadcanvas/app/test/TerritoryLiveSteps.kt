package kr.roadcanvas.app.test

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.ESTIMATED_AREA_LABEL
import kr.roadcanvas.app.challenge.ESTIMATED_CELLS_LABEL
import kr.roadcanvas.app.challenge.NEARBY_TERRITORY_TAG
import kr.roadcanvas.app.challenge.TERRITORY_READY_TITLE
import kr.roadcanvas.app.challenge.formatEstimatedArea
import kr.roadcanvas.app.challenge.formatEstimatedCells
import kr.roadcanvas.app.challenge.territoryEstimateOf
import kr.roadcanvas.app.modeselect.TERRITORY_START_LABEL
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.territorymap.DEFAULT_TERRITORY_MAP_CENTER
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 예상 영역은 일정 간격으로만 다시 계산하므로 그 간격보다 넉넉히 기다린다. */
private const val ESTIMATE_TIMEOUT_MILLIS = 10_000L

/**
 * 땅따먹기 준비 화면의 주변 영토와 진행 화면의 예상 영역·출발점 접근 안내 시나리오.
 * 서버 칸은 [TerritoryMapSteps], 진행 화면 위치 기록은 [ChallengeProgressSteps]의 단계를 함께 쓴다.
 */
class TerritoryLiveSteps(
    private val ruleHolder: ComposeRuleHolder,
    private val progressSteps: ChallengeProgressSteps,
    private val mapOverlaySteps: MapOverlaySteps
) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer

    @Given("땅따먹기 준비 화면을 열고 서울시청 위치를 받는다")
    fun openReadyAtCityHall() {
        composeRule.mainClock.autoAdvance = true
        progressSteps.openApp()
        composeRule.clickHomeButton(TERRITORY_START_LABEL)
        waitForText(TERRITORY_READY_TITLE)
        // 준비 화면이 위치를 구독한 뒤에 보내야 화면이 받는다.
        waitUntil { fakeContainer.locationProvider.subscriberCount.value > 0 }
        val center = DEFAULT_TERRITORY_MAP_CENTER
        runBlocking {
            fakeContainer.locationProvider.sendLocation(
                LocationSample(center.latitude, center.longitude, System.currentTimeMillis())
            )
        }
    }

    @Given("음성 안내가 꺼져 있다")
    fun disableVoiceGuide() {
        fakeContainer.voiceGuide.setEnabled(false)
    }

    @Then("주변 영토 안내는 {string}이다")
    fun assertNearbySummary(text: String) {
        val summary = hasTestTag(NEARBY_TERRITORY_TAG) and hasText(text)
        waitUntil { composeRule.onAllNodes(summary).fetchSemanticsNodes().isNotEmpty() }
    }

    @Then("{string} 버튼이 있다")
    fun assertButtonExists(label: String) {
        waitUntil {
            composeRule.onAllNodes(hasText(label) and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("음성 안내 설정이 꺼져 있다")
    fun assertVoiceGuideDisabled() {
        assertFalse(fakeContainer.voiceGuide.enabled.value)
    }

    /** 기록 세션의 경로로 같은 계산을 해 기대값을 정하고, 그 값이 화면에 나올 때까지 기다린다. */
    @Then("기록한 경로로 계산한 예상 면적과 예상 칸이 화면 왼쪽 조작 영역에 표시된다")
    fun assertEstimateShown() {
        val path = (fakeContainer.challengeRecorder.state.value as RecordingState.Recording).path
        val estimate = assertNotNull(territoryEstimateOf(path))
        val area = formatEstimatedArea(estimate)
        val cells = formatEstimatedCells(estimate)
        composeRule.waitUntil(ESTIMATE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(area).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(cells).fetchSemanticsNodes().isNotEmpty()
        }
        mapOverlaySteps.assertTextInControls(ESTIMATED_AREA_LABEL)
        mapOverlaySteps.assertTextInControls(area)
        mapOverlaySteps.assertTextInControls(ESTIMATED_CELLS_LABEL)
        mapOverlaySteps.assertTextInControls(cells)
    }

    @Then("음성 안내로 {string}를 읽었다")
    @Then("음성 안내로 {string}을 읽었다")
    fun assertSpoken(text: String) {
        waitUntil { text in fakeContainer.voiceGuide.spoken.value }
    }

    @Then("음성 안내를 {int}번 읽었다")
    fun assertSpokenCount(count: Int) {
        composeRule.waitForIdle()
        assertEquals(count, fakeContainer.voiceGuide.spoken.value.size)
    }

    private fun <T> assertNotNull(value: T?): T {
        assertNotNull("예상 영역이 없다", value)
        return value!!
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS, condition)
    }

    private fun waitForText(text: String) {
        waitUntil { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
