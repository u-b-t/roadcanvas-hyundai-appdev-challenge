package kr.roadcanvas.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import com.shopl.sdg_common.foundation.typography.SDGTypography

/**
 * SDG 글자 크기에 곱하는 차량용 배율. SDG는 손에 든 휴대폰 기준이라 팔을 뻗은 거리의 차량 화면에서는
 * 작으므로 크기와 줄 높이를 1.25배로 키운다. 글꼴(Pretendard)과 굵기는 SDG를 그대로 쓴다.
 */
const val CAR_TEXT_SCALE = 1.25f

/** SDG 글자 스타일을 [CAR_TEXT_SCALE]만큼 키운다. */
fun SDGTypography.forCar(): TextStyle = style.copy(
    fontSize = style.fontSize * CAR_TEXT_SCALE,
    lineHeight = style.lineHeight * CAR_TEXT_SCALE
)

/** Material 글자 역할에 SDG 스타일을 대응시킨 앱 글꼴. 크기는 Material 기본값과 비슷하게 맞췄다. */
val RoadCanvasTypography = Typography(
    displayLarge = SDGTypography.Special1SB.forCar(),
    displayMedium = SDGTypography.Special1SB.forCar(),
    displaySmall = SDGTypography.Point1SB.forCar(),
    headlineLarge = SDGTypography.Point2SB.forCar(),
    headlineMedium = SDGTypography.Title1SB.forCar(),
    headlineSmall = SDGTypography.Title2SB.forCar(),
    titleLarge = SDGTypography.Body1SB.forCar(),
    titleMedium = SDGTypography.Body2SB.forCar(),
    titleSmall = SDGTypography.Body3SB.forCar(),
    bodyLarge = SDGTypography.Body1R.forCar(),
    bodyMedium = SDGTypography.Body2R.forCar(),
    bodySmall = SDGTypography.Body3R.forCar(),
    labelLarge = SDGTypography.Body2SB.forCar(),
    labelMedium = SDGTypography.Body3SB.forCar(),
    labelSmall = SDGTypography.Body4SB.forCar()
)
