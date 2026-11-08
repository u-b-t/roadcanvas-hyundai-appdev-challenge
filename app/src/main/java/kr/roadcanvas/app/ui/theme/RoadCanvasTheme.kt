package kr.roadcanvas.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shopl.sdg_common.foundation.SDGColor

/**
 * SDG 색상으로 만든 앱 색 구성. SDG는 다크 모드를 제공하지 않으므로 기기 설정과 관계없이 이 구성만 쓴다.
 * 흰 글자를 올리는 색은 큰 글자 기준 대비(3:1) 이상인 진한 단계를 고른다.
 */
val RoadCanvasColorScheme: ColorScheme = lightColorScheme(
    primary = SDGColor.Primary400,
    onPrimary = SDGColor.Neutral0,
    primaryContainer = SDGColor.Primary50,
    onPrimaryContainer = SDGColor.Neutral700,
    inversePrimary = SDGColor.Primary200,
    secondary = SDGColor.Secondary400,
    onSecondary = SDGColor.Neutral0,
    secondaryContainer = SDGColor.Secondary50,
    onSecondaryContainer = SDGColor.Secondary400,
    tertiary = SDGColor.Neutral600,
    onTertiary = SDGColor.Neutral0,
    tertiaryContainer = SDGColor.SpecialMG,
    onTertiaryContainer = SDGColor.Neutral700,
    background = SDGColor.Neutral0,
    onBackground = SDGColor.Neutral700,
    surface = SDGColor.Neutral0,
    onSurface = SDGColor.Neutral700,
    surfaceVariant = SDGColor.Neutral150,
    onSurfaceVariant = SDGColor.Neutral600,
    surfaceTint = SDGColor.Neutral0,
    inverseSurface = SDGColor.Neutral700,
    inverseOnSurface = SDGColor.Neutral0,
    error = SDGColor.Red400,
    onError = SDGColor.Neutral0,
    errorContainer = SDGColor.Red50,
    onErrorContainer = SDGColor.Neutral700,
    outline = SDGColor.Neutral300,
    outlineVariant = SDGColor.Neutral200,
    scrim = SDGColor.Neutral900_a50,
    surfaceBright = SDGColor.Neutral0,
    surfaceDim = SDGColor.Neutral150,
    surfaceContainerLowest = SDGColor.Neutral0,
    surfaceContainerLow = SDGColor.Neutral50,
    surfaceContainer = SDGColor.Neutral100,
    surfaceContainerHigh = SDGColor.Neutral150,
    surfaceContainerHighest = SDGColor.Neutral200
)

/**
 * 앱 공통 테마. SDG 색상과 차량용으로 키운 Pretendard 글꼴을 MaterialTheme으로 제공하고,
 * 화면 전체를 SDG 배경색으로 채운다. 라이트 테마만 쓴다.
 */
@Composable
fun RoadCanvasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoadCanvasColorScheme, typography = RoadCanvasTypography) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
