package kr.roadcanvas.app.test

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.CHALLENGE_GOAL
import kr.roadcanvas.app.challenge.CHALLENGE_RULE_NO_PAUSE
import kr.roadcanvas.app.challenge.CHALLENGE_RULE_START_STOP
import kr.roadcanvas.app.challenge.CIRCLE_READY_TITLE
import kr.roadcanvas.app.challenge.ChallengeReadyScreen
import kr.roadcanvas.app.challenge.ChallengeReadyStatus
import kr.roadcanvas.app.challenge.READY_GUIDE_ICON_TAG
import kr.roadcanvas.app.modeselect.CIRCLE_CHALLENGE_NAME
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.ui.components.KeepWordsText
import kr.roadcanvas.app.ui.components.keepWordsTogether
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.vehicle.VehiclePermissions
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

class ChallengeReadySteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private var scenario: TestScreen<ComponentActivity>? = null

    private var guideText: String? = null
    private var guideLayout: TextLayoutResult? = null

    /** 준비 화면에 들어가기 전 주행 규제 구독자 수. 화면 전체의 정차 안내가 늘 구독한다. */
    private var baseRestrictionSubscribers = 0

    @Given("동그라미 챌린지 준비 화면을 연다")
    fun openChallengeReady() {
        composeRule.mainClock.autoAdvance = true
        scenario = TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        // 실제 앱과 같은 뒤로 가기 흐름을 위해 모드 선택에서 들어간다.
        waitForText(MODE_SELECT_TITLE)
        composeRule.waitForIdle()
        baseRestrictionSubscribers = fakeContainer.drivingRestrictionProvider.subscriberCount.value
        composeRule.onNode(hasText(CIRCLE_CHALLENGE_NAME) and hasClickAction()).performClick()
        waitForText(CIRCLE_READY_TITLE)
        // 준비 화면이 시작 조건을 구독한 뒤에 테스트 위치를 보내야 화면이 받는다.
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            fakeContainer.locationProvider.subscriberCount.value > 0
        }
    }

    @Given("위치 권한을 다시 묻지 않도록 거절한 준비 화면을 연다")
    fun openPermanentlyDeniedReady() {
        composeRule.mainClock.autoAdvance = true
        scenario = TestScreens.launchContent {
            ChallengeReadyScreen(
                status = ChallengeReadyStatus.PermissionRequired(
                    permissions = listOf(VehiclePermissions.FUSED_LOCATION),
                    permanentlyDenied = true
                ),
                onStart = {},
                onBack = {},
                onRequestPermission = {},
                onOpenSettings = {},
                onRetry = {}
            )
        }
        waitForText(CIRCLE_READY_TITLE)
    }

    @Then("안내 문장 {int}개가 각각 아이콘과 함께 표시된다")
    fun assertGuidesWithIcons(count: Int) {
        val icons = composeRule.onAllNodes(hasTestTag(READY_GUIDE_ICON_TAG)).fetchSemanticsNodes()
        assertEquals(count, icons.size)
        listOf(CHALLENGE_GOAL, CHALLENGE_RULE_START_STOP, CHALLENGE_RULE_NO_PAUSE)
            .take(count)
            .forEachIndexed { index, guide ->
                // 아이콘이 같은 줄의 문장 왼쪽에 붙어 있어야 한다.
                val icon = icons[index].boundsInRoot
                val text = composeRule.onNodeWithText(guide).fetchSemanticsNode().boundsInRoot
                assertTrue("아이콘 $icon, \"$guide\" $text", icon.right <= text.left)
                assertTrue(
                    "아이콘 $icon, \"$guide\" $text",
                    icon.center.y in text.top..text.bottom
                )
            }
    }

    @Given("안내 문장 {string}를 폭 {int}dp에 표시한다")
    fun showGuideInWidth(text: String, widthDp: Int) {
        composeRule.mainClock.autoAdvance = true
        guideText = text
        guideLayout = null
        TestScreens.launchContent {
            // 테마의 Surface가 최소 크기를 화면 전체로 넘기므로 Box로 받아 폭 제한이 적용되게 한다.
            Box {
                KeepWordsText(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(widthDp.dp),
                    onTextLayout = { guideLayout = it }
                )
            }
        }
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) { guideLayout != null }
    }

    @Then("안내 문장이 여러 줄로 표시된다")
    fun assertGuideWrapped() {
        composeRule.waitForIdle()
        val layout = requireNotNull(guideLayout)
        assertTrue("줄 수 ${layout.lineCount}", layout.lineCount > 1)
    }

    @Then("모든 줄이 띄어쓰기에서 바뀐다")
    fun assertLinesBreakAtSpaces() {
        composeRule.waitForIdle()
        val layout = requireNotNull(guideLayout)
        val shown = layout.layoutInput.text.text
        val lines = (0 until layout.lineCount).map { line ->
            shown.substring(layout.getLineStart(line), layout.getLineEnd(line))
        }
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue(
                "${line + 1}번째 줄이 단어 중간에서 바뀌었다: $lines",
                shown[end - 1].isWhitespace() || shown[end].isWhitespace()
            )
        }
        assertEquals(keepWordsTogether(requireNotNull(guideText)), shown)
    }

    @Then("{string} 안내가 {string} 버튼 바로 위에 있다")
    fun assertNoticeRightAboveButton(text: String, label: String) {
        waitForText(text)
        waitForButton(label, isEnabled() or isNotEnabled())
        val notice = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val button = composeRule.onNode(hasText(label) and hasClickAction())
            .getUnclippedBoundsInRoot()
        assertTrue("안내 $notice, 버튼 $button", notice.bottom <= button.top)
        assertTrue(
            "안내 $notice 와 버튼 $button 사이가 ${button.top - notice.bottom} 떨어져 있다",
            button.top - notice.bottom <= CONTROL_SPACING + 1.dp
        )
        assertTrue("안내 $notice, 버튼 $button", notice.left < button.right)
        assertTrue("안내 $notice, 버튼 $button", button.left < notice.right)
    }

    @Then("{string} 버튼이 화면에서 가장 큰 조작 요소다")
    fun assertLargestControl(label: String) {
        waitForButton(label, isEnabled() or isNotEnabled())
        val controls = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val target = composeRule.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode()
        val others = controls.filter { it.id != target.id }
        assertTrue("다른 조작 요소가 없다", others.isNotEmpty())
        val area = target.boundsInRoot.run { width * height }
        others.forEach { other ->
            val otherArea = other.boundsInRoot.run { width * height }
            assertTrue(
                "\"$label\" ${target.boundsInRoot} 보다 큰 조작 요소 ${other.boundsInRoot}",
                area > otherArea
            )
        }
    }

    @Then("{string} 버튼이 활성화되어 있다")
    fun assertButtonEnabled(label: String) {
        waitForButton(label, isEnabled())
    }

    @Then("{string} 버튼이 비활성화되어 있다")
    fun assertButtonDisabled(label: String) {
        waitForButton(label, isNotEnabled())
    }

    @Then("{string} 버튼이 표시된다")
    fun assertButtonDisplayed(label: String) {
        waitForButton(label, isEnabled())
        composeRule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
    }

    @Then("위치를 다시 요청한다")
    fun assertLocationRequestedAgain() {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            fakeContainer.locationProvider.requestCount.value >= 2
        }
    }

    @When("준비 화면에서 뒤로 가기를 누른다")
    fun pressBack() {
        composeRule.waitForIdle()
        requireNotNull(scenario).onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Then("시작 조건 확인 구독이 해제된다")
    fun assertUnsubscribed() {
        waitForText(MODE_SELECT_TITLE)
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            fakeContainer.locationProvider.subscriberCount.value == 0 &&
                fakeContainer.drivingRestrictionProvider.subscriberCount.value ==
                baseRestrictionSubscribers
        }
    }

    private fun waitForButton(label: String, state: SemanticsMatcher) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(label) and hasClickAction() and state)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
