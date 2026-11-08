package kr.roadcanvas.app.test

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.abs
import kr.roadcanvas.app.modeselect.MODE_ITEM_ARROW_TAG
import kr.roadcanvas.app.modeselect.MODE_SELECT_COVER_TAG
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.navigation.Routes
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.ui.theme.RoadCanvasColorScheme
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val COLOR_TOLERANCE = 0.05f

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

    @Then("{string} 항목은 강조색으로 채워진다")
    fun assertFilledWithPrimary(label: String) {
        val color = backgroundColorOf(label)
        assertTrue("$label 배경 $color", color.isCloseTo(RoadCanvasColorScheme.primary))
    }

    @Then("{string} 항목은 강조색으로 채워지지 않는다")
    @Then("{string} 버튼은 강조색으로 채워지지 않는다")
    fun assertNotFilledWithPrimary(label: String) {
        val color = backgroundColorOf(label)
        assertFalse("$label 배경 $color", color.isCloseTo(RoadCanvasColorScheme.primary))
    }

    @Then("{string} 항목은 {string} 항목보다 높다")
    @Then("{string} 항목은 {string} 버튼보다 높다")
    fun assertTaller(taller: String, shorter: String) {
        val tallerHeight = clickable(taller).getUnclippedBoundsInRoot().height
        val shorterHeight = clickable(shorter).getUnclippedBoundsInRoot().height
        assertTrue("$taller $tallerHeight, $shorter $shorterHeight", tallerHeight > shorterHeight)
    }

    @Then("{string} 항목의 높이는 최소 터치 크기의 2배 이상이다")
    fun assertDoubleTouchTarget(label: String) {
        clickable(label).assertHeightIsAtLeast(MIN_TOUCH_TARGET * 2)
    }

    @Then("{string} 버튼은 화면 아래 여백에 맞닿아 있다")
    fun assertAtBottomPadding(label: String) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = clickable(label).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals((root.bottom - SCREEN_PADDING).value, bounds.bottom.value, 1f)
    }

    @Then("{string} 항목에는 화살표가 있다")
    fun assertHasArrow(label: String) {
        itemInUnmergedTree(label).assert(hasAnyDescendant(hasTestTag(MODE_ITEM_ARROW_TAG)))
    }

    @Then("{string} 항목에는 화살표가 없다")
    fun assertHasNoArrow(label: String) {
        itemInUnmergedTree(label).assert(!hasAnyDescendant(hasTestTag(MODE_ITEM_ARROW_TAG)))
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

    private fun itemInUnmergedTree(label: String): SemanticsNodeInteraction = composeRule.onNode(
        hasClickAction() and hasAnyDescendant(hasText(label)),
        useUnmergedTree = true
    )

    /** 글자와 화살표가 없는 오른쪽 위 안쪽의 색을 항목이나 버튼의 배경색으로 본다. */
    private fun backgroundColorOf(label: String): Color {
        val pixels = clickable(label).captureToImage().toPixelMap()
        return pixels[(pixels.width * 0.95f).toInt(), (pixels.height * 0.15f).toInt()]
    }

    private fun Color.isCloseTo(other: Color): Boolean = abs(red - other.red) < COLOR_TOLERANCE &&
        abs(green - other.green) < COLOR_TOLERANCE &&
        abs(blue - other.blue) < COLOR_TOLERANCE

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
