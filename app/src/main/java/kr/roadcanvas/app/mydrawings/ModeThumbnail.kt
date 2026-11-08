package kr.roadcanvas.app.mydrawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.drawing.DrawingMode

/**
 * 도전 모드를 나타내는 그림. 동그라미 모드는 원 경로와 출발점을 그린다.
 * [backgroundColor]가 있으면 SDG 썸네일처럼 모서리를 둥글린 정사각형 위에 그린다.
 */
@Composable
fun ModeThumbnail(
    mode: DrawingMode,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    lineColor: Color = SDGColor.Primary400,
    backgroundColor: Color? = SDGColor.Primary300_a10
) {
    val background = backgroundColor?.let {
        Modifier.clip(SDGCornerRadius.BoxRadius.Radius12).background(it)
    } ?: Modifier
    Canvas(modifier = modifier.size(size).then(background)) {
        val strokeWidth = this.size.minDimension * 0.06f
        when (mode) {
            DrawingMode.CIRCLE -> {
                val radius = this.size.minDimension * 0.3f
                drawCircle(
                    color = lineColor,
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
                drawCircle(
                    color = lineColor,
                    radius = strokeWidth * 1.4f,
                    center = Offset(center.x, center.y + radius)
                )
            }
        }
    }
}
