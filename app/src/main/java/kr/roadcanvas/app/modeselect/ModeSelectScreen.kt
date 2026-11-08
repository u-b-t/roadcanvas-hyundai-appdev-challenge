package kr.roadcanvas.app.modeselect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import com.shopl.sdg_common.foundation.typography.SDGTypography
import kr.roadcanvas.app.ui.components.COVER_BACKGROUND_COLOR
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
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

/** 모드 목록과 내 그림 버튼을 담는 왼쪽 영역 너비. 16:9 화면에서 커버 이미지의 어두운 영역(약 36%) 안에 들어간다. */
private val MODE_LIST_WIDTH = 560.dp

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
            Text(
                text = MODE_SELECT_TITLE,
                style = MaterialTheme.typography.headlineLarge,
                color = ON_COVER_TEXT_COLOR
            )
            Column(
                modifier = Modifier.fillMaxWidth().testTag(MODE_SELECT_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
            ) {
                ModeListItem(
                    name = CIRCLE_CHALLENGE_NAME,
                    description = CIRCLE_CHALLENGE_DESCRIPTION,
                    onClick = onCircleChallengeClick
                )
                ModeListItem(
                    name = FREE_DRAW_NAME,
                    description = FREE_DRAW_DESCRIPTION,
                    onClick = { showFreeDrawNotice = true },
                    badge = COMING_SOON_LABEL
                )
            }
            CarButton(
                text = MY_DRAWINGS_LABEL,
                onClick = onMyDrawingsClick,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
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
        }
    )
}

@Composable
private fun ModeListItem(
    name: String,
    description: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        color = MODE_ITEM_COLOR,
        contentColor = ON_COVER_TEXT_COLOR,
        shape = SDGCornerRadius.BoxRadius.Radius16
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, style = MaterialTheme.typography.headlineSmall)
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        ComingSoonBadge(text = badge)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ON_COVER_SECONDARY_TEXT_COLOR
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = ON_COVER_SECONDARY_TEXT_COLOR
            )
        }
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
