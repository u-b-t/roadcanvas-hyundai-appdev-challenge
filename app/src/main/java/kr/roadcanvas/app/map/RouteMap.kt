package kr.roadcanvas.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.appContainer

/** 경로 지도를 그리는 컴포넌트. 테스트는 네트워크가 필요 없는 대체 컴포넌트를 주입한다. */
interface RouteMapComponent {
    @Composable
    fun Content(points: List<RoutePoint>, options: RouteMapOptions, modifier: Modifier)
}

/** [kr.roadcanvas.app.di.AppContainer]에서 받은 컴포넌트로 경로 지도를 그린다. */
@Composable
fun RouteMap(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
    options: RouteMapOptions = RouteMapOptions()
) {
    LocalContext.current.appContainer.routeMapComponent.Content(points, options, modifier)
}

/** 지도를 표시할 수 없거나 경로가 부족할 때 지도 영역에 보이는 안내 */
@Composable
fun RouteMapNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
