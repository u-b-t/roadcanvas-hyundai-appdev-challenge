package kr.roadcanvas.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.ui.theme.RoadCanvasTheme
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteConfig
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType

/**
 * 경로 지도 컴포넌트를 실제 화면(#18~#20)보다 먼저 확인하는 디버그 전용 화면.
 * `adb shell am start -n kr.roadcanvas.app/.debug.RouteMapPreviewActivity`로 연다.
 * 진행 중으로 바꾸면 모의 원 경로를 처음부터 재생하며 최신 위치를 따라간다.
 */
class RouteMapPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = SimulatedRouteGenerator.generate(SimulatedRouteType.CIRCLE)
            .map { RoutePoint(it.latitude, it.longitude) }
        setContent {
            RoadCanvasTheme {
                var inProgress by remember { mutableStateOf(false) }
                var playedCount by remember { mutableIntStateOf(1) }
                LaunchedEffect(inProgress) {
                    playedCount = 1
                    while (inProgress && playedCount < route.size) {
                        delay(SimulatedRouteConfig().intervalMillis)
                        playedCount++
                    }
                }
                val points = if (inProgress) route.take(playedCount) else route
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "모의 원 경로 · 지점 ${points.size}개",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = "진행 중", modifier = Modifier.padding(end = 8.dp))
                        Switch(checked = inProgress, onCheckedChange = { inProgress = it })
                    }
                    RouteMap(
                        points = points,
                        options = RouteMapOptions(inProgress = inProgress),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}
