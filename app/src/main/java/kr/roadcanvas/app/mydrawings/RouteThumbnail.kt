package kr.roadcanvas.app.mydrawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint

/** 썸네일의 접근성 설명. 경로 모양을 그렸는지 시험에서도 확인한다. */
const val ROUTE_THUMBNAIL_DESCRIPTION = "경로 썸네일"
const val MODE_THUMBNAIL_DESCRIPTION = "도전 모드 그림"

/** 썸네일 가장자리에서 경로까지 비우는 비율 */
private const val THUMBNAIL_INSET_RATIO = 0.16f

/**
 * 기록한 경로 모양을 그린 썸네일. 경로를 모서리를 둥글린 정사각형 안에 비율을 유지해 맞추고 출발점을 점으로 표시한다.
 * 경로 모양을 그릴 수 없으면(점이 2개 미만이거나 모두 같은 위치) [mode] 그림을 대신 그린다.
 */
@Composable
fun RouteThumbnail(
    points: List<RoutePoint>,
    mode: DrawingMode,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    lineColor: Color = SDGColor.Primary400,
    backgroundColor: Color = SDGColor.Primary300_a10
) {
    if (!canDrawRouteShape(points)) {
        ModeThumbnail(
            mode = mode,
            modifier = modifier.semantics { contentDescription = MODE_THUMBNAIL_DESCRIPTION },
            size = size,
            lineColor = lineColor,
            backgroundColor = backgroundColor
        )
        return
    }
    Canvas(
        modifier = modifier
            .size(size)
            .clip(SDGCornerRadius.BoxRadius.Radius12)
            .background(backgroundColor)
            .semantics { contentDescription = ROUTE_THUMBNAIL_DESCRIPTION }
    ) {
        val offsets = thumbnailOffsets(
            points = points,
            sizePx = this.size.minDimension,
            insetPx = this.size.minDimension * THUMBNAIL_INSET_RATIO
        )
        val strokeWidth = this.size.minDimension * 0.05f
        val path = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = lineColor, radius = strokeWidth * 1.4f, center = offsets.first())
    }
}

/** 점이 2개 이상이고 모두 같은 위치가 아니면 경로 모양을 그릴 수 있다. */
fun canDrawRouteShape(points: List<RoutePoint>): Boolean = points.size >= 2 &&
    points.any {
        it.latitude != points.first().latitude || it.longitude != points.first().longitude
    }

/**
 * [points]를 한 변이 [sizePx]인 정사각형 화면 좌표로 바꾼다. 북쪽이 위이고, 경도는 위도에 따라 줄여 실제 모양 비율을 유지한다.
 * 긴 쪽이 가장자리에서 [insetPx]만큼 안쪽에 닿도록 키우고 가운데에 둔다. 모두 같은 위치면 가운데 한 점이 된다.
 */
fun thumbnailOffsets(points: List<RoutePoint>, sizePx: Float, insetPx: Float): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val meanLatitude = points.sumOf { it.latitude } / points.size
    val longitudeScale = cos(meanLatitude * PI / 180)
    val xs = points.map { it.longitude * longitudeScale }
    val ys = points.map { it.latitude }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val span = max(maxX - minX, maxY - minY)
    val center = sizePx / 2
    if (span == 0.0) return points.map { Offset(center, center) }
    val scale = (sizePx - 2 * insetPx) / span
    val midX = (minX + maxX) / 2
    val midY = (minY + maxY) / 2
    return xs.indices.map { index ->
        Offset(
            x = (center + (xs[index] - midX) * scale).toFloat(),
            y = (center - (ys[index] - midY) * scale).toFloat()
        )
    }
}
