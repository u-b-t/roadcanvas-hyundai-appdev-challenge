package kr.roadcanvas.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.VehicleIntegrationInfo

/**
 * 디버그 빌드에서 스크린샷이 모의 결과인지 구분할 수 있도록 사용 중인 구현을 작게 표시한다.
 * 에뮬레이터 검증을 위해 현재 주행 규제 상태와 판단 기준(주행 상태 또는 UX 규제)도 함께 표시한다.
 */
@Composable
fun VehicleIntegrationBadge(
    info: VehicleIntegrationInfo,
    drivingRestriction: StateFlow<DrivingRestriction>,
    drivingRestrictionBasis: StateFlow<String?>,
    modifier: Modifier = Modifier
) {
    val restriction by drivingRestriction.collectAsState()
    val basis by drivingRestrictionBasis.collectAsState()
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        BadgeText(info.label)
        BadgeText("주행 규제 상태: ${restriction.label}")
        basis?.let { BadgeText("판단 기준: $it") }
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = Color.White)
}
