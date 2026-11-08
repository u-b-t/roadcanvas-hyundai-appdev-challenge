package kr.roadcanvas.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kr.roadcanvas.app.ui.theme.CONTROL_PANEL_WIDTH
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING

/**
 * 조작 버튼과 핵심 정보를 운전석에 가까운 화면 왼쪽 [controls]에 두고, 나머지 [content]를 오른쪽에 둔다.
 * 오른쪽에서 왼쪽으로 쓰는 언어 설정이어도 조작 영역은 왼쪽에 둔다. 각 영역 안의 배치 방향은 기기 설정을 따른다.
 */
@Composable
fun DriverSideLayout(
    controls: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    controlsWidth: Dp = CONTROL_PANEL_WIDTH
) {
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxSize().padding(SCREEN_PADDING),
            horizontalArrangement = Arrangement.spacedBy(SCREEN_PADDING)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Column(
                    modifier = Modifier.width(controlsWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
                    content = controls
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
    }
}

/**
 * 지도처럼 화면 전체를 채우는 [background] 위 왼쪽에 [controls]를 띄운다. 조작 영역은 [OverlayCard]로 감싸는 것을 기본으로 한다.
 * 오른쪽에서 왼쪽으로 쓰는 언어 설정이어도 조작 영역은 왼쪽에 둔다.
 */
@Composable
fun DriverSideOverlay(
    background: @Composable () -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    controlsWidth: Dp = CONTROL_PANEL_WIDTH
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier.fillMaxSize()) {
        background()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING)) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Column(
                        modifier = Modifier.align(Alignment.TopStart).width(controlsWidth),
                        verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
                        content = controls
                    )
                }
            }
        }
    }
}
