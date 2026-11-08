package kr.roadcanvas.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_resource.R as SdgR
import kr.roadcanvas.app.ui.components.CoverBackground
import kr.roadcanvas.app.ui.components.DriverSideOverlay

private const val GATED_CONTENT_KEY = "gatedContent"

/**
 * [reason]이 있으면 [content] 대신 정차 안내를 보여준다.
 * 가린 동안 화면을 구성에서 빼 대화상자까지 함께 숨기고, 저장 가능한 화면 상태는 남겨 정차하면 그대로 돌아온다.
 * 뒤로 가기도 막아 주행 중에는 화면을 바꾸지 않는다.
 */
@Composable
fun StopNoticeGate(reason: String?, content: @Composable () -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    if (reason == null) {
        stateHolder.SaveableStateProvider(GATED_CONTENT_KEY) { content() }
    } else {
        BackHandler {}
        StopNotice(reason)
    }
}

const val STOP_NOTICE_COVER_TAG = "stopNoticeCover"
const val STOP_NOTICE_ICON_TAG = "stopNoticeIcon"

/** 커버 이미지를 어둡게 덮는 정도. 배경의 경로 그림보다 안내 문장이 먼저 보이게 한다. */
const val STOP_NOTICE_DIM_ALPHA = 0.7f

/** 어두운 커버 위 아이콘과 글자 색 */
val STOP_NOTICE_ICON_COLOR = SDGColor.Primary400
val STOP_NOTICE_TITLE_COLOR = SDGColor.Neutral0
val STOP_NOTICE_REASON_COLOR = SDGColor.Neutral300

/** 정차 안내 문구 영역의 너비. 운전석 쪽 왼쪽에 두되 이유 문장이 한 줄에 들어가게 조작 영역보다 넓다. */
private val STOP_NOTICE_WIDTH = 960.dp

/** 한눈에 알아보도록 크게 그리는 경고 아이콘 크기 */
val STOP_NOTICE_ICON_SIZE = 160.dp

/**
 * 주행 중 가린 화면 대신 보여주는 안내. 모드 선택·시작 화면과 같은 커버 배경을 어둡게 깔고,
 * 큰 경고 아이콘과 짧은 문장을 운전석에 가까운 왼쪽 가운데에 둔다.
 */
@Composable
private fun StopNotice(reason: String) {
    DriverSideOverlay(
        background = {
            CoverBackground(
                modifier = Modifier.testTag(STOP_NOTICE_COVER_TAG),
                dimAlpha = STOP_NOTICE_DIM_ALPHA
            )
        },
        controlsWidth = STOP_NOTICE_WIDTH,
        verticalAlignment = Alignment.CenterVertically,
        controls = {
            Icon(
                painter = painterResource(SdgR.drawable.ic_common_warning),
                contentDescription = null,
                tint = STOP_NOTICE_ICON_COLOR,
                modifier = Modifier.size(STOP_NOTICE_ICON_SIZE).testTag(STOP_NOTICE_ICON_TAG)
            )
            Text(
                text = STOP_NOTICE_TITLE,
                style = MaterialTheme.typography.displayMedium,
                color = STOP_NOTICE_TITLE_COLOR
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.headlineSmall,
                color = STOP_NOTICE_REASON_COLOR
            )
        }
    )
}
