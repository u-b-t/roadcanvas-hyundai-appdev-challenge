package kr.roadcanvas.app.circleranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.FIT_CIRCLE_LEGEND
import kr.roadcanvas.app.challenge.RETRY_LABEL
import kr.roadcanvas.app.challenge.ScoreComponentRow
import kr.roadcanvas.app.challenge.fitCircle
import kr.roadcanvas.app.challenge.scoreComponents
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.mydrawings.DISTANCE_LABEL
import kr.roadcanvas.app.mydrawings.DURATION_LABEL
import kr.roadcanvas.app.mydrawings.formatDistance
import kr.roadcanvas.app.mydrawings.formatDuration
import kr.roadcanvas.app.mydrawings.formatScore
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val CIRCLE_RECORD_BACK_LABEL = "순위로"
const val CIRCLE_RECORD_TITLE_SUFFIX = "의 최고 기록"
const val CIRCLE_RECORD_DATE_LABEL = "기록 날짜"
const val CIRCLE_RECORD_SCORE_BREAKDOWN = "점수 구성"
const val CIRCLE_RECORD_NO_BREAKDOWN = "경로로 점수 구성을 계산하지 못했어요"
const val CIRCLE_RECORD_TAG = "circleRecordDetail"
const val CIRCLE_RECORD_OVERLAY_TAG = "circleRecordDetailOverlay"

/** 기록 상세를 불러오지 못한 이유 */
val CircleRankingFailure.recordText: String
    get() = when (this) {
        CircleRankingFailure.NOT_FOUND -> "순위 기록을 찾지 못했어요"
        CircleRankingFailure.INVALID_ROUTE -> "경로 일부를 불러오지 못했어요"
        else -> text
    }

/** 순위 기록 상세 화면에 서버 순위를 연결한다. */
@Composable
fun CircleRecordDetailRoute(uid: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: CircleRecordDetailViewModel = viewModel(
        key = "circleRecord-$uid",
        factory = viewModelFactory {
            initializer { CircleRecordDetailViewModel(context.appContainer.circleRanking, uid) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CircleRecordDetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        modifier = modifier
    )
}

/** 실제 경로와 맞춤 원을 지도 전체에 그리고, 운전석 쪽 왼쪽 오버레이에 순위·점수·점수 구성을 보여준다. */
@Composable
fun CircleRecordDetailScreen(
    state: CircleRecordUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DriverSideOverlay(
        modifier = modifier.testTag(CIRCLE_RECORD_TAG),
        background = {
            if (state is CircleRecordUiState.Loaded) {
                val result = state.detail.result
                val points = remember(result.path) {
                    result.path.map { RoutePoint(it.latitude, it.longitude) }
                }
                RouteMap(
                    points = points,
                    options = RouteMapOptions(
                        showEndpointMarkers = true,
                        inProgress = false,
                        contentPadding = driverSideOverlayContentPadding(),
                        referenceCircle = result.fitCircle
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(SDGColor.Neutral100))
            }
        },
        controls = {
            // 내용이 화면 높이를 넘으면 카드만 스크롤하고 순위로 버튼은 항상 보이게 둔다.
            CarButton(
                text = CIRCLE_RECORD_BACK_LABEL,
                onClick = onBack,
                style = CarButtonStyle.Line
            )
            OverlayCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag(CIRCLE_RECORD_OVERLAY_TAG)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailContent(state, onRetry)
                }
            }
        }
    )
}

@Composable
private fun DetailContent(state: CircleRecordUiState, onRetry: () -> Unit) {
    when (state) {
        CircleRecordUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        is CircleRecordUiState.Failed -> {
            Text(text = state.reason.recordText, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = CIRCLE_RANKING_RETRY_HINT,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CarButton(text = RETRY_LABEL, onClick = onRetry, modifier = Modifier.fillMaxWidth())
        }
        is CircleRecordUiState.Loaded -> LoadedContent(state.detail)
    }
}

@Composable
private fun LoadedContent(detail: CircleRecordDetail) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = detail.nickname + CIRCLE_RECORD_TITLE_SUFFIX,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (detail.mine) {
            Text(
                text = CIRCLE_RANKING_MINE_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(SDGCornerRadius.BoxRadius.Radius8)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = detail.rank?.let(::formatRank) ?: "-",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = formatScore(detail.score), style = MaterialTheme.typography.headlineMedium)
    }
    if (detail.rank == null) {
        Text(
            text = MY_CIRCLE_RANK_UNKNOWN,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            DISTANCE_LABEL to formatDistance(detail.result.distanceMeters),
            DURATION_LABEL to formatDuration(detail.result.durationMillis),
            CIRCLE_RECORD_DATE_LABEL to formatRankingDate(detail.achievedAtMillis)
        ).forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = value, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Text(text = CIRCLE_RECORD_SCORE_BREAKDOWN, style = MaterialTheme.typography.titleMedium)
    val components = detail.result.scoreComponents
    if (components.isEmpty()) {
        Text(
            text = detail.result.zeroReasonText ?: CIRCLE_RECORD_NO_BREAKDOWN,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            components.forEach { ScoreComponentRow(it) }
        }
        Text(
            text = FIT_CIRCLE_LEGEND,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
