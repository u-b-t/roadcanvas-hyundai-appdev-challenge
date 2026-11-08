package kr.roadcanvas.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.vehicle.VehicleIntegrationInfo

/** 디버그 빌드에서 스크린샷이 모의 결과인지 구분할 수 있도록 사용 중인 구현을 작게 표시한다. */
@Composable
fun VehicleIntegrationBadge(info: VehicleIntegrationInfo, modifier: Modifier = Modifier) {
    Text(
        text = info.label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
