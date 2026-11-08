package kr.roadcanvas.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING

/** 지도 위에서도 글자가 읽히도록 거의 불투명한 흰색을 쓴다. */
val OVERLAY_CARD_COLOR = SDGColor.Neutral0_a90

/** 지도와 구분되도록 주는 그림자 높이 */
val OVERLAY_CARD_ELEVATION = 8.dp

private val OverlayCardPadding = PaddingValues(24.dp)

/**
 * 지도 같은 배경 위에 정보와 조작 요소를 올리는 카드. SDG 기본 박스 모서리(20dp)와 반투명 흰 배경, 그림자를 쓴다.
 * 내용은 세로로 [CONTROL_SPACING] 간격을 두고 쌓는다.
 */
@Composable
fun OverlayCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = OverlayCardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = SDGCornerRadius.BoxRadius.Radius20,
        color = OVERLAY_CARD_COLOR,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = OVERLAY_CARD_ELEVATION
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
            content = content
        )
    }
}
