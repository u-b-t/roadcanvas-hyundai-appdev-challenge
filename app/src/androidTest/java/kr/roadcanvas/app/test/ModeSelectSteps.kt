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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.abs
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.modeselect.MODE_ITEM_ARROW_TAG
import kr.roadcanvas.app.modeselect.MODE_ITEM_PICTURE_TAG
import kr.roadcanvas.app.modeselect.MODE_SELECT_COVER_TAG
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.navigation.Routes
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.ui.theme.RoadCanvasColorScheme
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val COLOR_TOLERANCE = 0.05f

/** 배경색을 읽을 위쪽 여백 안 높이. 테두리(1dp)보다 안쪽이고 항목과 버튼의 위쪽 안쪽 여백(12dp 이상)보다 작다. */
private val BACKGROUND_SAMPLE_INSET = 6.dp

/** 배경색을 읽을 가로 위치(너비 비율). 둥근 모서리를 피한다. */
private val BACKGROUND_SAMPLE_FRACTIONS = listOf(0.3f, 0.5f, 0.7f)

class ModeSelectSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private var navController: NavHostController? = null
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer

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
    @Then("{string} 항목에 {string}가 표시된다")
    fun assertItemShows(item: String, text: String) {
        // 기록 요약은 화면을 연 뒤 저장소와 서버에서 읽어 채운다.
        val node = hasText(item) and hasText(text) and hasClickAction()
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(node).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(node).assertIsDisplayed()
    }

    @Given("서버에 내 땅 {int}칸이 있다")
    fun putMyCells(count: Int) {
        val owner = FakeTerritoryOwnerProvider.DEFAULT_OWNER
        fakeContainer.territoryStore.putCells(
            owner.uid,
            owner.nickname,
            List(count) { "wydm9q$it" }
        )
    }

    @Then("{string} 항목과 {string} 항목의 높이가 같다")
    fun assertSameHeight(first: String, second: String) {
        val firstHeight = clickable(first).getUnclippedBoundsInRoot().height
        val secondHeight = clickable(second).getUnclippedBoundsInRoot().height
        assertEquals(firstHeight.value, secondHeight.value, 1f)
    }

    @Then("{string} 항목에는 모드 그림이 있다")
    fun assertHasPicture(label: String) {
        itemInUnmergedTree(label).assert(hasAnyDescendant(hasTestTag(MODE_ITEM_PICTURE_TAG)))
    }

    @Then("{string} 항목은 강조색으로 채워진다")
    fun assertFilledWithPrimary(label: String) {
        val colors = backgroundColorsOf(label)
        assertTrue("$label 배경 $colors", colors.all { it.isCloseTo(RoadCanvasColorScheme.primary) })
    }

    @Then("{string} 항목은 강조색으로 채워지지 않는다")
    @Then("{string} 버튼은 강조색으로 채워지지 않는다")
    fun assertNotFilledWithPrimary(label: String) {
        val colors = backgroundColorsOf(label)
        assertFalse("$label 배경 $colors", colors.any { it.isCloseTo(RoadCanvasColorScheme.primary) })
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

    /**
     * 항목이나 버튼의 배경색을 여러 지점에서 읽는다. 글자·화살표·배지가 들어가지 않는 위쪽 안쪽 여백 띠를
     * 가로로 나눠 읽어, 한 지점에 다른 요소가 겹쳐도 판정이 한 픽셀에 좌우되지 않게 한다.
     */
    private fun backgroundColorsOf(label: String): List<Color> {
        val pixels = clickable(label).captureToImage().toPixelMap()
        val y = with(composeRule.density) { BACKGROUND_SAMPLE_INSET.roundToPx() }
        return BACKGROUND_SAMPLE_FRACTIONS.map { pixels[(pixels.width * it).toInt(), y] }
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
