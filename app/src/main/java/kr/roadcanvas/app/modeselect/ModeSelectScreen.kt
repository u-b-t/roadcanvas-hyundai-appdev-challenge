package kr.roadcanvas.app.modeselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import com.shopl.sdg_common.foundation.typography.SDGTypography
import kr.roadcanvas.app.ui.components.COVER_BACKGROUND_COLOR
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CoverBackground
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.ui.theme.forCar

const val MODE_SELECT_TITLE = "모드 선택"
const val CIRCLE_CHALLENGE_NAME = "동그라미 챌린지"
const val CIRCLE_CHALLENGE_DESCRIPTION = "원을 그리며 달리고 점수를 받아요"
const val FREE_DRAW_NAME = "자유그림"
const val FREE_DRAW_DESCRIPTION = "도로 위에 자유롭게 그려요"
const val COMING_SOON_LABEL = "준비 중"
const val FREE_DRAW_COMING_SOON_NOTICE = "자유그림은 아직 준비 중이에요"
const val MY_DRAWINGS_LABEL = "내 그림"
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

/** 준비 중 배지의 배경과 글자 색 */
val COMING_SOON_BADGE_COLOR = SDGColor.Neutral0
val ON_COMING_SOON_BADGE_COLOR = SDGColor.Neutral700

/** 준비 중이라 들어갈 수 없는 항목의 이름과 설명 글자 색. 일반 항목보다 흐리게 해 비활성으로 보이게 한다. */
val DISABLED_ON_COVER_TEXT_COLOR = SDGColor.Neutral400

/** 보조 동작인 내 그림 버튼의 테두리 색. 배경을 채우지 않아 모드 항목보다 덜 눈에 띈다. */
val SECONDARY_ON_COVER_BORDER_COLOR = SDGColor.Neutral0_a60

const val MODE_ITEM_ARROW_TAG = "modeItemArrow"

/** 모드 목록과 내 그림 버튼을 담는 왼쪽 영역 너비. 16:9 화면에서 커버 이미지의 어두운 영역(약 36%) 안에 들어간다. */
private val MODE_LIST_WIDTH = 560.dp

/** 남는 세로 공간을 나눌 때 동그라미 챌린지와 자유그림 항목의 비율. 주 동작인 동그라미 챌린지를 더 크게 둔다. */
private const val PRIMARY_ITEM_WEIGHT = 3f
private const val SECONDARY_ITEM_WEIGHT = 2f

private val ModeItemPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)

@Composable
fun ModeSelectScreen(
    onCircleChallengeClick: () -> Unit,
    onMyDrawingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 자유그림은 아직 제공하지 않으므로 누르면 안내만 표시한다.
    var showFreeDrawNotice by rememberSaveable { mutableStateOf(false) }

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
                    PrimaryModeItem(
                        name = CIRCLE_CHALLENGE_NAME,
                        description = CIRCLE_CHALLENGE_DESCRIPTION,
                        onClick = onCircleChallengeClick,
                        modifier = Modifier.weight(PRIMARY_ITEM_WEIGHT)
                    )
                    ComingSoonModeItem(
                        name = FREE_DRAW_NAME,
                        description = FREE_DRAW_DESCRIPTION,
                        onClick = { showFreeDrawNotice = true },
                        modifier = Modifier.weight(SECONDARY_ITEM_WEIGHT)
                    )
                }
                if (showFreeDrawNotice) {
                    Surface(
                        color = COMING_SOON_BADGE_COLOR,
                        contentColor = ON_COMING_SOON_BADGE_COLOR,
                        shape = SDGCornerRadius.BoxRadius.Radius16
                    ) {
                        Text(
                            text = FREE_DRAW_COMING_SOON_NOTICE,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                }
                SecondaryCoverButton(
                    text = MY_DRAWINGS_LABEL,
                    onClick = onMyDrawingsClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

/** 주 동작 항목. 강조색으로 채우고 이름과 설명을 가장 크게 쓴다. */
@Composable
private fun PrimaryModeItem(
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = SDGCornerRadius.BoxRadius.Radius20
    ) {
        Row(
            modifier = Modifier.padding(ModeItemPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = description, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag(MODE_ITEM_ARROW_TAG)
            )
        }
    }
}

/**
 * 준비 중이라 들어갈 수 없는 항목. 화살표 없이 흐린 글자와 준비 중 배지로 비활성임을 보이고,
 * 누르면 [onClick]으로 준비 중 안내를 띄울 수 있도록 누를 수는 있게 둔다.
 */
@Composable
private fun ComingSoonModeItem(
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .semantics { stateDescription = COMING_SOON_LABEL },
        color = MODE_ITEM_COLOR,
        contentColor = DISABLED_ON_COVER_TEXT_COLOR,
        shape = SDGCornerRadius.BoxRadius.Radius20
    ) {
        Box(
            modifier = Modifier.padding(ModeItemPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    ComingSoonBadge(text = COMING_SOON_LABEL)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = description, style = MaterialTheme.typography.titleMedium)
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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

/**
 * SDG Box Badge 모양의 배지. SDGBoxBadge는 높이가 20dp로 고정되어 차량용으로 키운 글자가 들어가지 않으므로
 * SDG 배지 모서리(6dp)와 차량용 글꼴로 같은 모양을 그린다.
 */
@Composable
private fun ComingSoonBadge(text: String) {
    Text(
        text = text,
        style = SDGTypography.Body3SB.forCar(),
        color = ON_COMING_SOON_BADGE_COLOR,
        modifier = Modifier
            .background(COMING_SOON_BADGE_COLOR, SDGCornerRadius.BoxRadius.Radius6)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
