package kr.roadcanvas.app.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import kotlinx.coroutines.delay
import kr.roadcanvas.app.R

const val SPLASH_DURATION_MILLIS = 2_000L
const val APP_NAME = "RoadCanvas"
const val SPLASH_COVER_TAG = "splashCover"

/** 커버 이미지 왼쪽의 어두운 단색 영역에서 글자가 시작하는 여백 */
private val SPLASH_TEXT_PADDING = 96.dp

/**
 * 커버 이미지를 화면 전체에 깔고 왼쪽 어두운 영역에 앱 이름을 둔다.
 * 이미지 오른쪽의 원 경로가 잘리지 않도록 오른쪽 기준으로 맞춘다.
 */
@Composable
fun SplashScreen(durationMillis: Long, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(durationMillis) {
        delay(durationMillis)
        currentOnFinished()
    }
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.road_canvas_cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = AbsoluteAlignment.CenterRight,
            modifier = Modifier.fillMaxSize().testTag(SPLASH_COVER_TAG)
        )
        // 이미지의 어두운 영역은 언어 설정과 관계없이 왼쪽에 있으므로 글자도 왼쪽에 둔다.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize().padding(start = SPLASH_TEXT_PADDING)) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Text(
                        text = APP_NAME,
                        style = MaterialTheme.typography.displayLarge,
                        color = SDGColor.Neutral0,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }
        }
    }
}
