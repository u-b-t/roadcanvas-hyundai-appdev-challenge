package kr.roadcanvas.app.splash

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import kotlinx.coroutines.delay
import kr.roadcanvas.app.ui.components.CoverBackground

/**
 * 시작 화면 표시 시간. 이어지는 모드 선택 화면도 같은 커버 배경이라 길게 두면 기다림만 길어지므로
 * 앱 이름을 읽을 만큼만 보여준다. 사용자가 정한 2초를 넘지 않는다.
 */
const val SPLASH_DURATION_MILLIS = 1_200L
const val APP_NAME = "RoadCanvas"
const val SPLASH_COVER_TAG = "splashCover"

/** 커버 이미지 왼쪽의 어두운 단색 영역에서 글자가 시작하는 여백 */
private val SPLASH_TEXT_PADDING = 96.dp

/**
 * 모드 선택 화면과 같은 [CoverBackground]를 깔고 왼쪽 어두운 영역에 앱 이름을 둔다.
 * 배경이 같아 모드 선택으로 넘어갈 때 글자만 바뀌어 보인다.
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
        CoverBackground(imageModifier = Modifier.testTag(SPLASH_COVER_TAG))
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
