package kr.roadcanvas.app.test

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.map.MapInsets
import kr.roadcanvas.app.map.RouteCameraPadding
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.CONTROL_PANEL_WIDTH
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING
import org.junit.Assert.assertTrue

private const val ROUTE_CAMERA_AREA_TAG = "route-camera-area"
private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 경로 지도 카메라 여백 기준으로 계산한 경로 영역이 지도 위 오버레이와 지도 컨트롤을 피하는지 확인한다. */
class RouteCameraSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule

    @When("지도 위 왼쪽 오버레이 화면에 경로 카메라 영역을 표시한다")
    fun showRouteCameraArea() {
        showRouteCameraArea(LayoutDirection.Ltr)
    }

    @When("오른쪽에서 왼쪽으로 쓰는 언어 설정에서 지도 위 왼쪽 오버레이 화면에 경로 카메라 영역을 표시한다")
    fun showRouteCameraAreaRtl() {
        showRouteCameraArea(LayoutDirection.Rtl)
    }

    @Then("경로 카메라 영역이 왼쪽 조작 영역과 겹치지 않는다")
    fun assertAreaClearOfControls() {
        val controlsRight =
            with(composeRule.density) { (SCREEN_PADDING + CONTROL_PANEL_WIDTH).toPx() }
        val area = routeCameraArea()
        assertTrue("경로 영역 $area 가 조작 영역(오른쪽 끝 $controlsRight)과 겹친다", area.left > controlsRight)
    }

    @Then("경로 카메라 영역이 오른쪽 끝 {int}dp 확대·축소 컨트롤 띠와 겹치지 않는다")
    fun assertAreaClearOfZoomControl(bandWidthDp: Int) {
        val bandLeft = rootWidth() - with(composeRule.density) { bandWidthDp * density }
        val area = routeCameraArea()
        assertTrue("경로 영역 $area 가 확대·축소 컨트롤 띠(왼쪽 끝 $bandLeft)와 겹친다", area.right <= bandLeft)
    }

    @Then("경로 카메라 영역이 화면 가장자리에서 떨어져 있다")
    fun assertAreaAwayFromEdges() {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val area = routeCameraArea()
        assertTrue(
            "경로 영역 $area 가 화면 $root 가장자리에 붙어 있다",
            area.left > root.left &&
                area.top > root.top &&
                area.right < root.right &&
                area.bottom < root.bottom
        )
    }

    /** 지도 자리에 경로 카메라 여백 기준으로 계산한 경로 영역을 그리고 왼쪽 오버레이를 올린다. */
    private fun showRouteCameraArea(layoutDirection: LayoutDirection) {
        TestScreens.launchContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                DriverSideOverlay(
                    background = { RouteCameraArea() },
                    controls = { OverlayCard { Text("주행 정보") } }
                )
            }
        }
    }

    @Composable
    private fun RouteCameraArea() {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
            if (size != IntSize.Zero) {
                val contentPadding = MapInsets.of(
                    driverSideOverlayContentPadding(),
                    density,
                    layoutDirection
                )
                val area = (contentPadding + RouteCameraPadding.insets(density, layoutDirection))
                    .inset(size.width, size.height)
                with(density) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(area.left, area.top) }
                            .size(area.width.toDp(), area.height.toDp())
                            .testTag(ROUTE_CAMERA_AREA_TAG)
                    )
                }
            }
        }
    }

    private fun routeCameraArea(): Rect {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(
                hasTestTag(ROUTE_CAMERA_AREA_TAG)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        return composeRule.onAllNodes(hasTestTag(ROUTE_CAMERA_AREA_TAG))
            .fetchSemanticsNodes().first().boundsInRoot
    }

    private fun rootWidth(): Float = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
}
