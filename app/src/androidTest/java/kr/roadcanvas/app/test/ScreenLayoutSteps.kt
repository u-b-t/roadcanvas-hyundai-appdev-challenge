package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import io.cucumber.java.en.Then
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

    private fun boundsOf(matcher: SemanticsMatcher) = run {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(matcher).getUnclippedBoundsInRoot()
    }
}
