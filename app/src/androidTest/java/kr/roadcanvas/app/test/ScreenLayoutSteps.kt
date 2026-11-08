package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.width
import io.cucumber.java.en.Then
import kr.roadcanvas.app.navigation.STOP_NOTICE_COVER_TAG
import kr.roadcanvas.app.navigation.STOP_NOTICE_ICON_SIZE
import kr.roadcanvas.app.navigation.STOP_NOTICE_ICON_TAG
import kr.roadcanvas.app.navigation.STOP_NOTICE_TITLE
import kr.roadcanvas.app.splash.SPLASH_COVER_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 화면 요소가 운전석 쪽(왼쪽)에 놓였는지 위치로 확인하는 시나리오 단계 */
class ScreenLayoutSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule

    @Then("{string}가 화면 왼쪽 절반에 표시된다")
    @Then("{string}이 화면 왼쪽 절반에 표시된다")
    fun assertTextOnLeftHalf(text: String) {
        val bounds = boundsOf(hasText(text))
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val center = (root.left + root.right) / 2
        assertTrue("\"$text\" $bounds, 화면 $root", bounds.right <= center)
    }

    @Then("{string} 버튼이 {string} 안내보다 왼쪽에 있다")
    fun assertButtonLeftOfText(label: String, text: String) {
        val button = boundsOf(hasText(label) and hasClickAction())
        val notice = boundsOf(hasText(text))
        assertTrue("버튼 $button, 안내 $notice", button.right <= notice.left)
    }

    @Then("커버 이미지가 화면 전체에 표시된다")
    fun assertCoverFillsScreen() {
        val cover = boundsOf(hasTestTag(SPLASH_COVER_TAG))
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(root, cover)
    }

    @Then("정차 안내의 커버 배경이 화면 전체에 표시된다")
    fun assertStopNoticeCoverFillsScreen() {
        val cover = boundsOf(hasTestTag(STOP_NOTICE_COVER_TAG))
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(root, cover)
    }

    @Then("정차 안내 경고 아이콘이 문장 위 화면 왼쪽 절반에 크게 표시된다")
    fun assertStopNoticeIcon() {
        val icon = boundsOf(hasTestTag(STOP_NOTICE_ICON_TAG))
        val title = boundsOf(hasText(STOP_NOTICE_TITLE))
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("아이콘 $icon, 제목 $title", icon.bottom <= title.top)
        assertTrue("아이콘 $icon, 화면 $root", icon.right <= (root.left + root.right) / 2)
        assertTrue("아이콘 $icon", icon.width >= STOP_NOTICE_ICON_SIZE)
    }

    private fun boundsOf(matcher: SemanticsMatcher) = run {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(
                matcher,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(matcher, useUnmergedTree = true).getUnclippedBoundsInRoot()
    }
}
