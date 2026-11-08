package kr.roadcanvas.app.test

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.DriverSideLayout
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.ui.theme.RoadCanvasTheme
import org.junit.Assert.assertTrue

private const val CONTROLS_TAG = "designSystemControls"
private const val CONTENT_TAG = "designSystemContent"
private const val THEME_SAMPLE_TEXT = "테마 확인"

class DesignSystemSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule

    // 모드 선택 화면은 어두운 커버 이미지를 배경으로 쓰므로, 테마의 배경만 그린 화면으로 확인한다.
    @Given("기기가 다크 모드인 상태로 앱 테마로 감싼 화면을 연다")
    fun openThemedScreenInDarkMode() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                RoadCanvasTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(THEME_SAMPLE_TEXT)
                    }
                }
            }
        }
    }

    @Then("화면 배경이 밝은 색이다")
    fun assertLightBackground() {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        // 여백인 왼쪽 위 모서리 근처의 색을 본다.
        val background = pixels[4, 4]
        assertTrue("배경 밝기 ${background.luminance()}", background.luminance() > 0.9f)
    }

    @Given("글자가 {string}인 차량용 버튼을 그린다")
    fun drawCarButton(text: String) {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            Box { CarButton(text = text, onClick = {}) }
        }
    }

    @Then("{string} 버튼의 너비는 최소 터치 크기 이상이다")
    fun assertWideTouchTarget(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction())
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Given("운전석 쪽 배치로 조작 영역과 내용 영역을 그린다")
    fun drawDriverSideLayout() {
        drawWithDirection(LayoutDirection.Ltr) { DriverSideSample() }
    }

    @Given("오른쪽에서 왼쪽으로 쓰는 언어 설정에서 운전석 쪽 배치로 조작 영역과 내용 영역을 그린다")
    fun drawDriverSideLayoutRtl() {
        drawWithDirection(LayoutDirection.Rtl) { DriverSideSample() }
    }

    @Given("오른쪽에서 왼쪽으로 쓰는 언어 설정에서 지도 위 오버레이 배치로 조작 영역을 그린다")
    fun drawDriverSideOverlayRtl() {
        drawWithDirection(LayoutDirection.Rtl) {
            DriverSideOverlay(
                background = { Box(modifier = Modifier.fillMaxSize().testTag(CONTENT_TAG)) },
                controls = {
                    OverlayCard(modifier = Modifier.testTag(CONTROLS_TAG)) { Text("조작") }
                }
            )
        }
    }

    @Then("조작 영역이 내용 영역보다 왼쪽에 있다")
    fun assertControlsLeftOfContent() {
        val controls = composeRule.onNodeWithTag(CONTROLS_TAG).getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertTrue("조작 $controls, 내용 $content", controls.right <= content.left)
    }

    @Then("조작 영역이 화면 왼쪽 절반에 있다")
    fun assertControlsOnLeftHalf() {
        val controls = composeRule.onNodeWithTag(CONTROLS_TAG).getUnclippedBoundsInRoot()
        val screen = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        val center = (screen.left + screen.right) / 2
        assertTrue("조작 $controls, 화면 $screen", controls.right <= center)
    }

    private fun drawWithDirection(direction: LayoutDirection, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
        }
    }
}

@Composable
private fun DriverSideSample() {
    DriverSideLayout(
        controls = { Box(modifier = Modifier.testTag(CONTROLS_TAG)) { Text("조작") } },
        content = { Box(modifier = Modifier.fillMaxSize().testTag(CONTENT_TAG)) }
    )
}
