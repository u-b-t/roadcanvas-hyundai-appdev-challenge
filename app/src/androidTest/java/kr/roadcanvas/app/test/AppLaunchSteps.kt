package kr.roadcanvas.app.test

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.MainActivity
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.navigation.Routes
import kr.roadcanvas.app.splash.APP_NAME
import kr.roadcanvas.app.splash.SPLASH_COVER_TAG
import kr.roadcanvas.app.ui.components.COVER_BACKGROUND_COLOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val FRAME_MILLIS = 16L

class AppLaunchSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private var scenario: TestScreen<out Activity>? = null
    private var navController: NavHostController? = null

    @Given("앱을 실행한다")
    fun launchApp() {
        // 시작 화면 표시 시간을 가상 시계로 검증하기 위해 자동 진행을 멈춘다.
        composeRule.mainClock.autoAdvance = false
        scenario = TestScreens.launch(MainActivity::class.java)
    }

    @Given("시작 화면 표시 시간을 {double}초로 줄여 앱 화면을 연다")
    fun launchWithSplashDuration(seconds: Double) {
        scenario = TestScreens.launchContent {
            val controller = rememberNavController()
            navController = controller
            RoadCanvasNavHost(
                navController = controller,
                splashDurationMillis = (seconds * 1_000).toLong()
            )
        }
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
        // 이동 거리와 출발점까지 거리처럼 같은 글이 여러 곳에 보일 수 있다.
        composeRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    @Then("앱 창 배경이 커버의 어두운 색이다")
    fun assertWindowBackground() {
        requireNotNull(scenario).onActivity { activity ->
            val background = activity.window.decorView.background
            assertTrue("창 배경 $background", background is ColorDrawable)
            assertEquals(
                COVER_BACKGROUND_COLOR.toArgb(),
                (background as ColorDrawable).color
            )
        }
    }

    @Then("모드 선택 화면이 나타나는 동안 시작 화면의 커버 이미지가 화면 전체에 남아 있다")
    fun assertSplashCoverStaysDuringTransition() {
        // 가상 시계를 조금씩 진행해 모드 선택 화면이 처음 구성된 때를 잡는다. 이때는 나타나는 효과가 막 시작된 상태다.
        var waited = 0L
        while (composeRule.onAllNodesWithText(MODE_SELECT_TITLE).fetchSemanticsNodes().isEmpty()) {
            assertTrue("모드 선택 화면이 나타나지 않았다", waited < WAIT_TIMEOUT_MILLIS)
            composeRule.mainClock.advanceTimeByFrame()
            waited += FRAME_MILLIS
        }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val cover = composeRule.onNodeWithTag(SPLASH_COVER_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(root, cover)
        composeRule.mainClock.autoAdvance = true
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

    @Then("시작 화면이 다시 표시되지 않는다")
    fun assertSplashNotShownAgain() {
        // 기기에 따라 뒤로 가기 후 화면이 닫히거나 남아 있을 수 있어 두 경우 모두 확인한다.
        if (requireNotNull(scenario).isStarted) {
            composeRule.onAllNodesWithText(APP_NAME).assertCountEquals(0)
        }
        val controller = requireNotNull(navController)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThrows(IllegalArgumentException::class.java) {
                controller.getBackStackEntry(Routes.SPLASH)
            }
        }
    }
}
