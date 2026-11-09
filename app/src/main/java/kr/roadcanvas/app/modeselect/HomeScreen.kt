package kr.roadcanvas.app.modeselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.mydrawings.ModeThumbnail
import kr.roadcanvas.app.splash.APP_NAME
import kr.roadcanvas.app.territorymap.TERRITORY_MAP_LABEL
import kr.roadcanvas.app.ui.components.COVER_BACKGROUND_COLOR
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CoverBackground
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val HOME_TITLE = APP_NAME
const val CIRCLE_CHALLENGE_NAME = "동그라미 챌린지"
const val CIRCLE_CHALLENGE_DESCRIPTION = "원을 그리며 달리고 점수를 받아요"
const val CIRCLE_START_LABEL = "동그라미 그리기"
const val CIRCLE_RANKING_HOME_LABEL = "동그라미 순위"
const val FREE_DRAW_NAME = "자유그림"
const val FREE_DRAW_DESCRIPTION = "도로 위에 자유롭게 그려요"
const val FREE_DRAW_START_LABEL = "자유그림 그리기"
const val GALLERY_LABEL = "공개 작품"
const val TERRITORY_NAME = "땅따먹기"
const val TERRITORY_DESCRIPTION = "한 바퀴 돌아 둘러싼 땅을 차지해요"
const val TERRITORY_START_LABEL = "땅따먹기 시작"
const val MY_DRAWINGS_LABEL = "내 그림"
const val HOME_SCREEN_TAG = "homeScreen"
const val HOME_COVER_TAG = "homeCover"
const val HOME_SECTIONS_TAG = "homeSections"
const val HOME_SECTION_TAG = "homeSection"
const val HOME_SECTION_PICTURE_TAG = "homeSectionPicture"
const val HOME_SECTION_SUMMARY_TAG = "homeSectionSummary"

/** 커버 이미지 왼쪽 어두운 영역의 색. 섹션 뒤 배경과 글자 대비 기준으로 쓴다. */
val COVER_DARK_COLOR = COVER_BACKGROUND_COLOR

/** 어두운 배경 위 모드 섹션의 배경. 배경과 구분될 만큼만 밝힌다. */
val HOME_SECTION_COLOR = SDGColor.Neutral0_a10

/** 어두운 배경 위 이름과 제목 글자 색 */
val ON_COVER_TEXT_COLOR = SDGColor.Neutral0

/** 어두운 배경 위 설명 글자 색 */
val ON_COVER_SECONDARY_TEXT_COLOR = SDGColor.Neutral300

/** 보조 버튼의 테두리 색. 배경을 채우지 않아 강조색으로 채운 시작 버튼보다 덜 눈에 띈다. */
val SECONDARY_ON_COVER_BORDER_COLOR = SDGColor.Neutral0_a60

/** 어두운 배경 위 내 기록 요약 글자 색. 흰 이름과 구분되도록 밝은 강조색을 쓴다. */
val ON_COVER_SUMMARY_COLOR = SDGColor.Primary200

/** 섹션과 내 그림 버튼을 담는 왼쪽 영역 너비. 16:9 화면에서 커버 이미지의 어두운 영역(약 36%) 안에 들어간다. */
private val HOME_PANEL_WIDTH = 560.dp

private val SectionPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)

/** 섹션 머리 왼쪽 모드 그림 크기 */
private val MODE_PICTURE_SIZE = 64.dp

/** 시작 버튼이 보조 버튼보다 넓도록 나누는 비율 */
private const val START_BUTTON_WEIGHT = 1.4f

/** 한 모드 섹션에 보여줄 내용과 동작 */
private class HomeSection(
    val mode: DrawingMode,
    val description: String,
    val startLabel: String,
    val onStart: () -> Unit,
    val secondaryLabel: String,
    val onSecondary: () -> Unit
)

/**
 * 메인 화면. 커버 이미지 위 왼쪽에 앱 이름과 내 그림 버튼, 그 아래 모드별 섹션을 둔다.
 * 섹션마다 모드 이름, 한 줄 설명과 내 기록 요약을 보이고, 강조색으로 채운 시작 버튼과 보조 버튼을 나란히 둔다.
 * 섹션이 한 화면에 들어가지 않으면 섹션 영역만 세로로 스크롤한다.
 */
@Composable
fun HomeScreen(
    onCircleChallengeClick: () -> Unit,
    onCircleRankingClick: () -> Unit,
    onTerritoryClick: () -> Unit,
    onTerritoryMapClick: () -> Unit,
    onFreeDrawingClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onMyDrawingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    recordSummary: ModeRecordSummary? = null
) {
    val sections = listOf(
        HomeSection(
            mode = DrawingMode.CIRCLE,
            description = CIRCLE_CHALLENGE_DESCRIPTION,
            startLabel = CIRCLE_START_LABEL,
            onStart = onCircleChallengeClick,
            secondaryLabel = CIRCLE_RANKING_HOME_LABEL,
            onSecondary = onCircleRankingClick
        ),
        HomeSection(
            mode = DrawingMode.TERRITORY,
            description = TERRITORY_DESCRIPTION,
            startLabel = TERRITORY_START_LABEL,
            onStart = onTerritoryClick,
            secondaryLabel = TERRITORY_MAP_LABEL,
            onSecondary = onTerritoryMapClick
        ),
        HomeSection(
            mode = DrawingMode.FREE_DRAWING,
            description = FREE_DRAW_DESCRIPTION,
            startLabel = FREE_DRAW_START_LABEL,
            onStart = onFreeDrawingClick,
            secondaryLabel = GALLERY_LABEL,
            onSecondary = onGalleryClick
        )
    )
    DriverSideOverlay(
        modifier = modifier.testTag(HOME_SCREEN_TAG),
        controlsWidth = HOME_PANEL_WIDTH,
        background = { CoverBackground(imageModifier = Modifier.testTag(HOME_COVER_TAG)) },
        controls = {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
            ) {
                // 모든 모드의 기록을 모아 보는 내 그림은 어느 섹션에도 속하지 않으므로 제목 줄에 둔다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
                ) {
                    Text(
                        text = HOME_TITLE,
                        style = MaterialTheme.typography.headlineLarge,
                        color = ON_COVER_TEXT_COLOR,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryCoverButton(text = MY_DRAWINGS_LABEL, onClick = onMyDrawingsClick)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag(HOME_SECTIONS_TAG),
                    verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
                ) {
                    sections.forEach { ModeSection(section = it, summary = recordSummary) }
                }
            }
        }
    )
}

/**
 * 모드 섹션. 밝힌 배경 위 머리에 모드 그림, 흰 이름, 흐린 한 줄 설명과 내 기록 요약을 쓰고 아래에 버튼을 둔다.
 * [summary]를 읽는 동안에는 요약 자리를 비워 둔다. 머리 글은 하나로 합쳐 섹션을 이름으로 찾을 수 있게 한다.
 */
@Composable
private fun ModeSection(section: HomeSection, summary: ModeRecordSummary?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(HOME_SECTION_TAG),
        color = HOME_SECTION_COLOR,
        contentColor = ON_COVER_TEXT_COLOR,
        shape = SDGCornerRadius.BoxRadius.Radius20
    ) {
        Column(
            modifier = Modifier.padding(SectionPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ModeThumbnail(
                    mode = section.mode,
                    size = MODE_PICTURE_SIZE,
                    lineColor = ON_COVER_SUMMARY_COLOR,
                    backgroundColor = HOME_SECTION_COLOR,
                    modifier = Modifier.testTag(HOME_SECTION_PICTURE_TAG)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.mode.displayName,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.titleMedium,
                        color = ON_COVER_SECONDARY_TEXT_COLOR
                    )
                    Text(
                        text = summary?.textFor(section.mode).orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = ON_COVER_SUMMARY_COLOR,
                        modifier = Modifier.testTag(HOME_SECTION_SUMMARY_TAG)
                    )
                }
            }
            // 시작 버튼은 강조색으로 채우고 더 넓게 두어 섹션에서 가장 먼저 눈에 띄게 한다.
            Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING)) {
                CarButton(
                    text = section.startLabel,
                    onClick = section.onStart,
                    modifier = Modifier.weight(START_BUTTON_WEIGHT)
                )
                SecondaryCoverButton(
                    text = section.secondaryLabel,
                    onClick = section.onSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 어두운 커버 위 보조 버튼. [CarButton]의 선 스타일은 밝은 배경용 어두운 글자라 커버 위에서 읽히지 않으므로,
 * 같은 모서리·글꼴·터치 크기로 흰 글자와 흐린 흰 테두리만 그린다.
 */
@Composable
private fun SecondaryCoverButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET),
        shape = SDGCornerRadius.BoxRadius.Radius16,
        border = BorderStroke(1.dp, SECONDARY_ON_COVER_BORDER_COLOR),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ON_COVER_TEXT_COLOR),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
