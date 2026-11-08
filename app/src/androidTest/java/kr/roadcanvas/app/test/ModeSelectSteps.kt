package kr.roadcanvas.app.test

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.modeselect.MIN_TOUCH_TARGET
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

class ModeSelectSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var navController: NavHostController? = null

    @Given("모드 선택 화면을 연다")
    fun openModeSelect() {
        composeRule.mainClock.autoAdvance = true
        val launched = ActivityScenario.launch(ComponentActivity::class.java)
        launched.onActivity { activity ->
            activity.setContent {
                val controller = rememberNavController()
                navController = controller
                MaterialTheme {
                    RoadCanvasNavHost(navController = controller, splashDurationMillis = 0L)
                }
            }
        }
        scenario = launched
        waitForText(MODE_SELECT_TITLE)
    }

    @Then("{string} 카드와 {string} 카드가 가로로 나란히 표시된다")
    fun assertCardsSideBySide(left: String, right: String) {
        val leftBounds = clickable(left).assertIsDisplayed().getUnclippedBoundsInRoot()
        val rightBounds = clickable(right).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("$left 카드가 $right 카드 왼쪽에 있어야 한다", leftBounds.right <= rightBounds.left)
        assertEquals(leftBounds.top.value, rightBounds.top.value, 1f)
        assertEquals(leftBounds.bottom.value, rightBounds.bottom.value, 1f)
    }

    @Then("{string} 카드의 높이는 최소 터치 크기 이상이다")
    @Then("{string} 버튼의 높이는 최소 터치 크기 이상이다")
    fun assertLargeTouchTarget(label: String) {
        clickable(label).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @When("{string} 카드를 누른다")
    @When("{string} 버튼을 누른다")
    fun click(label: String) {
        clickable(label).performClick()
    }

    @Then("{string} 카드에 {string}이 표시된다")
    fun assertCardShows(card: String, text: String) {
        composeRule.onNode(hasText(card) and hasText(text) and hasClickAction())
            .assertIsDisplayed()
    }

    @Then("모드 선택 화면에 머문다")
    fun assertStillOnModeSelect() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText(MODE_SELECT_TITLE).assertIsDisplayed()
        val controller = requireNotNull(navController)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(Routes.MODE_SELECT, controller.currentDestination?.route)
        }
    }

    @After
    fun closeScreen() {
        scenario?.close()
    }

    private fun clickable(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction())

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
