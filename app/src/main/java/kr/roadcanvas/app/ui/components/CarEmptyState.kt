package kr.roadcanvas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.spacing.SDGSpacing

/** SDG Empty 템플릿의 일러스트 크기(140dp)를 차량 화면 배율만큼 키운 값 */
private val EmptyIllustrationSize = 176.dp

/**
 * 표시할 내용이 없거나 화면을 쓸 수 없을 때 보여주는 안내. SDG Empty 템플릿의 구성(원형 배경 일러스트, 제목, 보조 문구)을
 * 차량 글자 크기로 그린다. 버튼은 넣지 않으므로 필요한 조작 요소는 운전석 쪽 조작 영역에 둔다.
 */
@Composable
fun CarEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    illustrationBackground: Color = SDGColor.Primary300_a10,
    illustration: @Composable () -> Unit
) {
    val textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) {
        TextAlign.Center
    } else {
        TextAlign.Start
    }
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(SDGSpacing.Spacing20)
    ) {
        Box(
            modifier = Modifier
                .size(EmptyIllustrationSize)
                .background(color = illustrationBackground, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            illustration()
        }
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(SDGSpacing.Spacing10)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign
            )
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = textAlign
                )
            }
        }
    }
}
