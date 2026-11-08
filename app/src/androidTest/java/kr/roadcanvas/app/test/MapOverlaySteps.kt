package kr.roadcanvas.app.test

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.map.naver.NaverRouteMapComponent
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.CONTROL_PANEL_WIDTH
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 지도를 화면 전체에 깔고 정보와 조작 요소를 왼쪽 오버레이로 올린 화면의 배치를 확인한다. */
class MapOverlaySteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule

    /** 화면 왼쪽 가장자리부터 조작 영역 오른쪽 끝까지의 너비(픽셀) */
    private val controlsRight: Float
        get() = with(composeRule.density) { (SCREEN_PADDING + CONTROL_PANEL_WIDTH).toPx() }

    @When("지도 키 없이 네이버 경로 지도를 화면 전체에 깔고 왼쪽 오버레이에 {string}를 올린다")
    fun showNaverMapWithoutKeyUnderOverlay(info: String) {
        val component = NaverRouteMapComponent(MapAuth(clientId = ""))
        val points = listOf(RoutePoint(37.5665, 126.978), RoutePoint(37.5674, 126.978))
        TestScreens.launchContent {
            DriverSideOverlay(
                background = {
                    component.Content(
                        points,
                        RouteMapOptions(contentPadding = driverSideOverlayContentPadding()),
                        Modifier.fillMaxSize()
                    )
                },
                controls = { OverlayCard { Text(info) } }
            )
        }
    }

    @Then("{string} 버튼이 화면 왼쪽 조작 영역에 있다")
    fun assertButtonInControls(label: String) {
        val bounds = boundsOf(hasText(label) and hasClickAction())
        assertTrue(
            "$label 버튼 $bounds 가 조작 영역(오른쪽 끝 $controlsRight) 밖에 있다",
            bounds.right <= controlsRight
        )
    }

    @Then("{string}가 화면 왼쪽 조작 영역에 표시된다")
    @Then("{string}이 화면 왼쪽 조작 영역에 표시된다")
    fun assertTextInControls(text: String) {
        val bounds = boundsOf(hasText(text))
        assertTrue(
            "$text $bounds 가 조작 영역(오른쪽 끝 $controlsRight) 밖에 있다",
            bounds.right <= controlsRight
        )
    }

    @Then("{string}로 시작하는 지도 표시가 왼쪽 조작 영역을 피해 표시된다")
    fun assertMapContentOutsideControls(prefix: String) {
        val bounds = boundsOf(hasText(prefix, substring = true))
        assertTrue(
            "$prefix $bounds 가 조작 영역(오른쪽 끝 $controlsRight)과 겹친다",
            bounds.left >= controlsRight
        )
    }

    private fun boundsOf(matcher: SemanticsMatcher): Rect {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        return composeRule.onAllNodes(matcher).fetchSemanticsNodes().first().boundsInRoot
    }
}
