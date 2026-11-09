package kr.roadcanvas.app.test

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.abs
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.modeselect.HOME_COVER_TAG
import kr.roadcanvas.app.modeselect.HOME_SCREEN_TAG
import kr.roadcanvas.app.modeselect.HOME_SECTION_PICTURE_TAG
import kr.roadcanvas.app.modeselect.HOME_SECTION_TAG
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.ui.theme.RoadCanvasColorScheme
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val COLOR_TOLERANCE = 0.05f

/** 배경색을 읽을 위쪽 여백 안 높이. 테두리(1dp)보다 안쪽이고 버튼의 위쪽 안쪽 여백(12dp)보다 작다. */
private val BACKGROUND_SAMPLE_INSET = 6.dp

/** 배경색을 읽을 가로 위치(너비 비율). 둥근 모서리를 피한다. */
private val BACKGROUND_SAMPLE_FRACTIONS = listOf(0.3f, 0.5f, 0.7f)

class HomeSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer

    @Given("메인 화면을 연다")
    fun openHome() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        composeRule.waitForHome()
    }

    @Then("메인 화면이 표시된다")
    fun assertHomeShown() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForHome()
        composeRule.onNodeWithTag(HOME_SCREEN_TAG).assertIsDisplayed()
    }

    @Then("커버 이미지가 화면 전체를 채운다")
    fun assertCoverFillsScreen() {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val cover = composeRule.onNodeWithTag(HOME_COVER_TAG).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(root.left.value, cover.left.value, 1f)
        assertEquals(root.top.value, cover.top.value, 1f)
        assertEquals(root.right.value, cover.right.value, 1f)
        assertEquals(root.bottom.value, cover.bottom.value, 1f)
    }

    @Then("{string} 섹션 아래에 {string} 섹션이 있다")
    fun assertSectionsStacked(upper: String, lower: String) {
        val upperBounds = section(upper).getUnclippedBoundsInRoot()
        val lowerBounds = section(lower).getUnclippedBoundsInRoot()
        assertTrue("$upper 섹션이 $lower 섹션 위에 있어야 한다", upperBounds.bottom <= lowerBounds.top)
        assertEquals(upperBounds.left.value, lowerBounds.left.value, 1f)
    }

    @Then("{string} 섹션에 {string}이 표시된다")
    @Then("{string} 섹션에 {string}가 표시된다")
    fun assertSectionShows(name: String, text: String) {
        // 기록 요약은 화면을 연 뒤 저장소와 서버에서 읽어 채운다.
        val node = sectionMatcher(name) and hasText(text)
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(node).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(node).performScrollTo().assertIsDisplayed()
    }

    @Then("{string} 섹션에는 모드 그림이 있다")
    fun assertSectionHasPicture(name: String) {
        composeRule.onNode(sectionMatcher(name), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(HOME_SECTION_PICTURE_TAG)))
    }

    @Then("{string} 섹션 안에 {string} 버튼이 있다")
    fun assertButtonInSection(name: String, label: String) {
        composeRule.onNode(buttonInUnmergedTree(label), useUnmergedTree = true)
            .assert(hasAnyAncestor(sectionMatcher(name)))
    }

    @Then("{string} 버튼은 어느 섹션에도 속하지 않는다")
    fun assertButtonOutsideSections(label: String) {
        composeRule.onNode(buttonInUnmergedTree(label), useUnmergedTree = true)
            .assert(!hasAnyAncestor(hasTestTag(HOME_SECTION_TAG)))
    }

    @Then("{string} 버튼은 {string} 섹션 위에 있다")
    fun assertButtonAboveSection(label: String, name: String) {
        val button = composeRule.homeButton(label).getUnclippedBoundsInRoot()
        val section = section(name).getUnclippedBoundsInRoot()
        assertTrue("$label $button, $name $section", button.bottom <= section.top)
    }

    @Then("{string} 버튼은 화면 왼쪽 절반에 있다")
    fun assertOnLeftHalf(label: String) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = composeRule.homeButton(label).assertIsDisplayed().getUnclippedBoundsInRoot()
        val center = (root.left + root.right) / 2
        assertTrue("$label $bounds, 화면 $root", bounds.right <= center)
    }

    @Then("{string} 버튼의 높이는 최소 터치 크기 이상이다")
    fun assertLargeTouchTarget(label: String) {
        clickable(label).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @When("{string} 버튼을 누른다")
    fun click(label: String) {
        clickable(label).performClick()
    }

    @When("메인 화면에서 {string} 버튼을 누른다")
    fun clickOnHome(label: String) {
        composeRule.clickHomeButton(label)
    }

    @When("섹션 영역을 {string} 버튼이 보일 때까지 스크롤한다")
    fun scrollTo(label: String) {
        composeRule.homeButton(label)
    }

    @Then("{string} 버튼이 화면 안에 보인다")
    fun assertInsideScreen(label: String) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = clickable(label).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(
            "$label $bounds, 화면 $root",
            bounds.top >= root.top && bounds.bottom <= root.bottom
        )
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

    @Then("{string} 버튼은 강조색으로 채워진다")
    fun assertFilledWithPrimary(label: String) {
        val colors = backgroundColorsOf(label)
        assertTrue("$label 배경 $colors", colors.all { it.isCloseTo(RoadCanvasColorScheme.primary) })
    }

    @Then("{string} 버튼은 강조색으로 채워지지 않는다")
    fun assertNotFilledWithPrimary(label: String) {
        val colors = backgroundColorsOf(label)
        assertFalse("$label 배경 $colors", colors.any { it.isCloseTo(RoadCanvasColorScheme.primary) })
    }

    @Then("{string} 버튼은 {string} 버튼보다 넓다")
    fun assertWider(wider: String, narrower: String) {
        val widerWidth = clickable(wider).getUnclippedBoundsInRoot().width
        val narrowerWidth = clickable(narrower).getUnclippedBoundsInRoot().width
        assertTrue("$wider $widerWidth, $narrower $narrowerWidth", widerWidth > narrowerWidth)
    }

    private fun clickable(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction())

    private fun sectionMatcher(name: String): SemanticsMatcher =
        hasTestTag(HOME_SECTION_TAG) and (hasText(name) or hasAnyDescendant(hasText(name)))

    private fun section(name: String): SemanticsNodeInteraction =
        composeRule.onNode(sectionMatcher(name)).performScrollTo()

    private fun buttonInUnmergedTree(label: String): SemanticsMatcher =
        hasClickAction() and hasAnyDescendant(hasText(label))

    /**
     * 버튼의 배경색을 여러 지점에서 읽는다. 글자가 들어가지 않는 위쪽 안쪽 여백 띠를
     * 가로로 나눠 읽어, 한 지점에 다른 요소가 겹쳐도 판정이 한 픽셀에 좌우되지 않게 한다.
     */
    private fun backgroundColorsOf(label: String): List<Color> {
        val pixels = composeRule.homeButton(label).captureToImage().toPixelMap()
        val y = with(composeRule.density) { BACKGROUND_SAMPLE_INSET.roundToPx() }
        return BACKGROUND_SAMPLE_FRACTIONS.map { pixels[(pixels.width * it).toInt(), y] }
    }

    private fun Color.isCloseTo(other: Color): Boolean = abs(red - other.red) < COLOR_TOLERANCE &&
        abs(green - other.green) < COLOR_TOLERANCE &&
        abs(blue - other.blue) < COLOR_TOLERANCE
}
