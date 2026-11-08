package kr.roadcanvas.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.R

/**
 * 커버 이미지 왼쪽 어두운 영역의 색. 이미지에서 잰 값이며 앱 시작 창 배경(`cover_background`)도 같은 색이다.
 * 이미지가 그려지기 전과 이미지 둘레를 이 색으로 채워 화면이 바뀌어도 배경이 이어져 보이게 한다.
 */
val COVER_BACKGROUND_COLOR = Color(0xFF020818)

/** 왼쪽 글자 뒤를 어둡게 덮는 영역 너비. 화면 비율이 달라 이미지의 어두운 영역이 좁아져도 글자 대비를 유지한다. */
val COVER_SCRIM_WIDTH = 760.dp

/**
 * 커버 이미지를 화면 전체에 깔고 왼쪽을 어두운 색으로 덮는다. 오른쪽 원 경로가 잘리지 않도록 오른쪽 기준으로 맞춘다.
 * 이미지의 어두운 영역은 언어 설정과 관계없이 왼쪽에 있으므로 덮는 영역도 언제나 왼쪽에 둔다.
 * [dimAlpha]만큼 이미지 전체를 같은 색으로 어둡게 해 배경보다 글자가 먼저 보이게 할 수 있다.
 */
@Composable
fun CoverBackground(
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    scrimWidth: Dp = COVER_SCRIM_WIDTH,
    dimAlpha: Float = 0f
) {
    Box(modifier = modifier.fillMaxSize().background(COVER_BACKGROUND_COLOR)) {
        Image(
            painter = painterResource(R.drawable.road_canvas_cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = AbsoluteAlignment.CenterRight,
            modifier = imageModifier.fillMaxSize()
        )
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(COVER_BACKGROUND_COLOR.copy(alpha = dimAlpha))
            )
        }
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.CenterLeft)
                .width(scrimWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to COVER_BACKGROUND_COLOR,
                        0.75f to COVER_BACKGROUND_COLOR,
                        1f to Color.Transparent
                    )
                )
        )
    }
}
