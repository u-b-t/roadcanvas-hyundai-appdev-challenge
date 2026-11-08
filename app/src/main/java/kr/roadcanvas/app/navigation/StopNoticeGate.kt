package kr.roadcanvas.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_resource.R as SdgR
import kr.roadcanvas.app.ui.components.CarEmptyState
import kr.roadcanvas.app.ui.components.DriverSideLayout

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

/** 정차 안내 문구 영역의 너비. 운전석 쪽 왼쪽에 두되 두 줄 안내가 한 줄씩 들어가게 조작 영역보다 넓다. */
private val STOP_NOTICE_WIDTH = 640.dp

/** 주행 중 가린 화면 대신 보여주는 안내. SDG 빈 상태 구성으로 운전석에 가까운 왼쪽에 둔다. */
@Composable
private fun StopNotice(reason: String) {
    DriverSideLayout(
        controlsWidth = STOP_NOTICE_WIDTH,
        controls = {
            Spacer(modifier = Modifier.weight(1f))
            CarEmptyState(
                title = STOP_NOTICE_TITLE,
                body = reason,
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    painter = painterResource(SdgR.drawable.ic_common_warning),
                    contentDescription = null,
                    tint = SDGColor.Primary400,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        },
        content = {}
    )
}
