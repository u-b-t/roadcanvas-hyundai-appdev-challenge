package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.modeselect.MODE_SELECT_COVER_TAG
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.navigation.Routes
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

class ModeSelectSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private var navController: NavHostController? = null

    @Given("모드 선택 화면을 연다")
    fun openModeSelect() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            val controller = rememberNavController()
            navController = controller
            RoadCanvasNavHost(navController = controller, splashDurationMillis = 0L)
        }
        waitForText(MODE_SELECT_TITLE)
    }

    @Then("커버 이미지가 화면 전체를 채운다")
    fun assertCoverFillsScreen() {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val cover = composeRule.onNodeWithTag(MODE_SELECT_COVER_TAG).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(root.left.value, cover.left.value, 1f)
        assertEquals(root.top.value, cover.top.value, 1f)
        assertEquals(root.right.value, cover.right.value, 1f)
        assertEquals(root.bottom.value, cover.bottom.value, 1f)
    }

    @Then("{string} 항목 아래에 {string} 항목이 표시된다")
    fun assertItemsStacked(upper: String, lower: String) {
        val upperBounds = clickable(upper).assertIsDisplayed().getUnclippedBoundsInRoot()
        val lowerBounds = clickable(lower).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("$upper 항목이 $lower 항목 위에 있어야 한다", upperBounds.bottom <= lowerBounds.top)
        assertEquals(upperBounds.left.value, lowerBounds.left.value, 1f)
    }

    @Then("{string} 항목은 화면 왼쪽 절반에 있다")
    @Then("{string} 버튼은 화면 왼쪽 절반에 있다")
    fun assertOnLeftHalf(label: String) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = clickable(label).assertIsDisplayed().getUnclippedBoundsInRoot()
        val center = (root.left + root.right) / 2
        assertTrue("$label $bounds, 화면 $root", bounds.right <= center)
    }

    @Then("{string} 항목의 높이는 최소 터치 크기 이상이다")
    @Then("{string} 버튼의 높이는 최소 터치 크기 이상이다")
    fun assertLargeTouchTarget(label: String) {
        clickable(label).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @When("{string} 항목을 누른다")
    @When("{string} 버튼을 누른다")
    fun click(label: String) {
        clickable(label).performClick()
    }

    @Then("{string} 항목에 {string}이 표시된다")
    fun assertItemShows(item: String, text: String) {
        composeRule.onNode(hasText(item) and hasText(text) and hasClickAction())
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

    private fun clickable(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction())

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
