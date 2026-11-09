package kr.roadcanvas.app.modeselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.mydrawings.ModeThumbnail
import kr.roadcanvas.app.circleranking.CIRCLE_RANKING_LABEL
import kr.roadcanvas.app.territorymap.TERRITORY_MAP_LABEL
import kr.roadcanvas.app.ui.components.COVER_BACKGROUND_COLOR
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CoverBackground
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val MODE_SELECT_TITLE = "모드 선택"
const val CIRCLE_CHALLENGE_NAME = "동그라미 챌린지"
const val CIRCLE_CHALLENGE_DESCRIPTION = "원을 그리며 달리고 점수를 받아요"
const val FREE_DRAW_NAME = "자유그림"
const val FREE_DRAW_DESCRIPTION = "도로 위에 자유롭게 그려요"
const val TERRITORY_NAME = "땅따먹기"
const val TERRITORY_DESCRIPTION = "한 바퀴 돌아 둘러싼 땅을 차지해요"
const val MY_DRAWINGS_LABEL = "내 그림"
const val GALLERY_LABEL = "공개 작품"
const val MODE_SELECT_COVER_TAG = "modeSelectCover"
const val MODE_SELECT_LIST_TAG = "modeSelectList"

/** 커버 이미지 왼쪽 어두운 영역의 색. 목록 뒤 그라데이션과 글자 대비 기준으로 쓴다. */
val COVER_DARK_COLOR = COVER_BACKGROUND_COLOR

/** 어두운 배경 위 모드 항목의 배경. 배경과 구분될 만큼만 밝힌다. */
val MODE_ITEM_COLOR = SDGColor.Neutral0_a10

/** 어두운 배경 위 이름과 제목 글자 색 */
val ON_COVER_TEXT_COLOR = SDGColor.Neutral0

/** 어두운 배경 위 설명 글자 색 */
val ON_COVER_SECONDARY_TEXT_COLOR = SDGColor.Neutral300

/** 보조 동작인 내 그림 버튼의 테두리 색. 배경을 채우지 않아 모드 항목보다 덜 눈에 띈다. */
val SECONDARY_ON_COVER_BORDER_COLOR = SDGColor.Neutral0_a60

const val MODE_ITEM_ARROW_TAG = "modeItemArrow"

/** 모드 목록과 내 그림 버튼을 담는 왼쪽 영역 너비. 16:9 화면에서 커버 이미지의 어두운 영역(약 36%) 안에 들어간다. */
private val MODE_LIST_WIDTH = 560.dp

private val ModeItemPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

/** 카드 왼쪽 모드 그림 크기 */
private val MODE_PICTURE_SIZE = 72.dp

/** 어두운 배경 위 내 기록 요약 글자 색. 흰 이름과 구분되도록 밝은 강조색을 쓴다. */
val ON_COVER_SUMMARY_COLOR = SDGColor.Primary200

const val MODE_ITEM_PICTURE_TAG = "modeItemPicture"
const val MODE_ITEM_SUMMARY_TAG = "modeItemSummary"

@Composable
fun ModeSelectScreen(
    onCircleChallengeClick: () -> Unit,
    onFreeDrawingClick: () -> Unit,
    onMyDrawingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onGalleryClick: () -> Unit = {},
    onTerritoryClick: () -> Unit = {},
    onTerritoryMapClick: () -> Unit = {},
    recordSummary: ModeRecordSummary? = null,
    onCircleRankingClick: () -> Unit = {}
) {
    DriverSideOverlay(
        modifier = modifier,
        controlsWidth = MODE_LIST_WIDTH,
        background = { CoverBackground(imageModifier = Modifier.testTag(MODE_SELECT_COVER_TAG)) },
        controls = {
            // 목록이 왼쪽 영역 세로를 채우고 내 그림 버튼은 아래 끝에 오도록 남는 높이를 항목에 나눈다.
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
            ) {
                Text(
                    text = MODE_SELECT_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = ON_COVER_TEXT_COLOR
                )
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag(MODE_SELECT_LIST_TAG),
                    verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
                ) {
                    // 세 모드는 같은 크기와 강조로 보여 고르기 쉽게 한다.
                    ModeItem(
                        mode = DrawingMode.CIRCLE,
                        description = CIRCLE_CHALLENGE_DESCRIPTION,
                        summary = recordSummary,
                        onClick = onCircleChallengeClick,
                        modifier = Modifier.weight(1f)
                    )
                    ModeItem(
                        mode = DrawingMode.FREE_DRAWING,
                        description = FREE_DRAW_DESCRIPTION,
                        summary = recordSummary,
                        onClick = onFreeDrawingClick,
                        modifier = Modifier.weight(1f)
                    )
                    ModeItem(
                        mode = DrawingMode.TERRITORY,
                        description = TERRITORY_DESCRIPTION,
                        summary = recordSummary,
                        onClick = onTerritoryClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 보조 동작인 내 그림, 공개 작품, 영토 지도와 동그라미 순위는 아래 끝에 나란히 둔다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
                ) {
                    SecondaryCoverButton(
                        text = MY_DRAWINGS_LABEL,
                        onClick = onMyDrawingsClick,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryCoverButton(
                        text = GALLERY_LABEL,
                        onClick = onGalleryClick,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryCoverButton(
                        text = TERRITORY_MAP_LABEL,
                        onClick = onTerritoryMapClick,
                        modifier = Modifier.weight(1f)
                    )
                    // 글자가 짧아 남는 너비를 다른 버튼에 준다.
                    SecondaryCoverButton(
                        text = CIRCLE_RANKING_LABEL,
                        onClick = onCircleRankingClick
                    )
                }
            }
        }
    )
}

/**
 * 모드 항목. 밝힌 배경 위에 모드 그림, 흰 이름, 흐린 한 줄 설명과 내 기록 요약을 쓴다.
 * [summary]를 읽는 동안에는 요약 자리를 비워 둔다.
 */
@Composable
private fun ModeItem(
    mode: DrawingMode,
    description: String,
    summary: ModeRecordSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        color = MODE_ITEM_COLOR,
        contentColor = ON_COVER_TEXT_COLOR,
        shape = SDGCornerRadius.BoxRadius.Radius20
    ) {
        Row(
            modifier = Modifier.padding(ModeItemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ModeThumbnail(
                mode = mode,
                size = MODE_PICTURE_SIZE,
                lineColor = ON_COVER_SUMMARY_COLOR,
                backgroundColor = MODE_ITEM_COLOR,
                modifier = Modifier.testTag(MODE_ITEM_PICTURE_TAG)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = mode.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleMedium,
                    color = ON_COVER_SECONDARY_TEXT_COLOR
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary?.textFor(mode).orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = ON_COVER_SUMMARY_COLOR,
                    modifier = Modifier.testTag(MODE_ITEM_SUMMARY_TAG)
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = ON_COVER_SECONDARY_TEXT_COLOR,
                modifier = Modifier.testTag(MODE_ITEM_ARROW_TAG)
            )
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
        // 버튼 세 개가 한 줄에 들어가도록 가로 여백을 줄여 글자가 한 줄에 보이게 한다.
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
