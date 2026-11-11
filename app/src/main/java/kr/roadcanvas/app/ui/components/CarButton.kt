package kr.roadcanvas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

/** [CarButton]의 강조 단계 */
enum class CarButtonStyle {
    /** 화면의 주 동작. SDG Primary 색으로 채운다. */
    Primary,

    /** 보조 동작. SDG Neutral 배경으로 채운다. */
    Neutral,

    /** 덜 중요한 동작. SDG 선 버튼처럼 테두리만 그린다. */
    Line
}

private val ButtonContentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/**
 * 차량용 버튼. SDG 버튼(SDGBoxButton)은 높이가 42dp 이하로 고정되어 차량 터치 영역에 못 미치므로,
 * SDG 색·모서리·글꼴로 같은 모양을 그리되 높이와 너비를 [MIN_TOUCH_TARGET] 이상으로 보장한다.
 */
@Composable
fun CarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CarButtonStyle = CarButtonStyle.Primary,
    enabled: Boolean = true,
    icon: NavigationIcon? = null
) {
    val sizedModifier = modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
    val shape = SDGCornerRadius.BoxRadius.Radius16
    val label: @Composable () -> Unit = {
        if (icon != null) {
            NavigationButtonIcon(icon)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
    when (style) {
        CarButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            contentPadding = ButtonContentPadding
        ) { label() }

        CarButtonStyle.Neutral -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = SDGColor.Neutral150,
                contentColor = SDGColor.Neutral700
            ),
            contentPadding = ButtonContentPadding
        ) { label() }

        CarButtonStyle.Line -> OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, SDGColor.Neutral300),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SDGColor.Neutral700),
            contentPadding = ButtonContentPadding
        ) { label() }
    }
}
