package kr.roadcanvas.app.test

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.MainActivity
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

private const val WAIT_TIMEOUT_MILLIS = 5_000L

class AppLaunchSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private var scenario: ActivityScenario<out Activity>? = null

    @Given("앱을 실행한다")
    fun launchApp() {
        // 시작 화면 표시 시간을 가상 시계로 검증하기 위해 자동 진행을 멈춘다.
        composeRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Given("시작 화면 표시 시간을 {double}초로 줄여 앱 화면을 연다")
    fun launchWithSplashDuration(seconds: Double) {
        val launched = ActivityScenario.launch(ComponentActivity::class.java)
        launched.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    RoadCanvasNavHost(splashDurationMillis = (seconds * 1_000).toLong())
                }
            }
        }
        scenario = launched
    }

    @When("{double}초가 지난다")
    @When("{double}초가 더 지난다")
    fun advanceTime(seconds: Double) {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy((seconds * 1_000).toLong())
    }

    @Then("화면에 {string}가 표시된다")
    @Then("화면에 {string}이 표시된다")
    fun assertTextDisplayed(text: String) {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Then("화면 방향이 가로로 고정되어 있다")
    fun assertLandscape() {
        requireNotNull(scenario).onActivity { activity ->
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                activity.requestedOrientation
            )
        }
    }

    @When("뒤로 가기를 누른다")
    fun pressBack() {
        composeRule.waitForIdle()
        requireNotNull(scenario).onActivity { activity ->
            (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed()
        }
    }

    @Then("시작 화면으로 돌아가지 않고 앱 화면이 닫힌다")
    fun assertClosedWithoutSplash() {
        // API 31 이상에서 태스크 루트의 뒤로 가기는 시스템이 비동기로 처리한다.
        val launched = requireNotNull(scenario)
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (launched.state.isAtLeast(Lifecycle.State.STARTED) &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(50)
        }
        assertFalse(
            "뒤로 가기 후 앱 화면이 닫혀야 합니다. 현재 상태: ${launched.state}",
            launched.state.isAtLeast(Lifecycle.State.STARTED)
        )
    }

    @After
    fun closeApp() {
        scenario?.close()
    }
}
