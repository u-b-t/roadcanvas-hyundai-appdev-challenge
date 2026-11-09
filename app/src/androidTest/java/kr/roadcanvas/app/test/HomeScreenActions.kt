package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kr.roadcanvas.app.modeselect.HOME_SCREEN_TAG
import kr.roadcanvas.app.modeselect.HOME_SECTIONS_TAG

private const val HOME_WAIT_TIMEOUT_MILLIS = 5_000L

/** 메인 화면이 그려질 때까지 기다린다. 시작 화면도 같은 앱 이름을 보이므로 화면 태그로 찾는다. */
fun ComposeTestRule.waitForHome() {
    waitUntil(HOME_WAIT_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(HOME_SCREEN_TAG)).fetchSemanticsNodes().isNotEmpty()
    }
}

/** 메인 화면의 [label] 버튼. 섹션 영역이 스크롤되므로 보이도록 스크롤한 뒤 돌려준다. */
fun ComposeTestRule.homeButton(label: String): SemanticsNodeInteraction {
    val button = hasText(label) and hasClickAction()
    waitUntil(HOME_WAIT_TIMEOUT_MILLIS) { onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
    val node = onNode(button)
    return if (onAllNodes(button and hasAnyAncestor(hasTestTag(HOME_SECTIONS_TAG)))
            .fetchSemanticsNodes().isNotEmpty()
    ) {
        node.performScrollTo()
    } else {
        node
    }
}

/** 메인 화면에서 [label] 버튼을 누른다. */
fun ComposeTestRule.clickHomeButton(label: String) {
    waitForHome()
    homeButton(label).performClick()
}
