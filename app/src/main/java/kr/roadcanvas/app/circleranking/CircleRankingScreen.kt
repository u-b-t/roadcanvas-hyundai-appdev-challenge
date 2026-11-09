package kr.roadcanvas.app.circleranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.mydrawings.formatScore
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideLayout
import kr.roadcanvas.app.ui.components.OverlayCard

const val CIRCLE_RANKING_LABEL = "순위"
const val CIRCLE_RANKING_TITLE = "동그라미 챌린지 순위"
const val CIRCLE_RANKING_SUBTITLE = "최고 점수 상위 10명"
const val MY_CIRCLE_RECORD_TITLE = "내 최고 기록"
const val CIRCLE_RANKING_REFRESH_LABEL = "새로고침"
const val CIRCLE_RANKING_LOADING = "불러오는 중"
const val CIRCLE_RANKING_EMPTY = "아직 순위에 오른 사용자가 없어요"
const val MY_CIRCLE_RECORD_EMPTY = "아직 순위에 오른 기록이 없어요"
const val MY_CIRCLE_RECORD_EMPTY_BODY = "동그라미 챌린지를 끝내면 최고 점수가 순위에 올라가요"
const val MY_CIRCLE_RANK_UNKNOWN = "순위를 확인하지 못했어요"
const val CIRCLE_RANKING_RETRY_HINT = "새로고침해 다시 불러와 주세요"
const val CIRCLE_RANKING_MINE_LABEL = "나"
const val CIRCLE_RANKING_ROW_TAG = "circleRankingRow"
const val MY_CIRCLE_RANKING_ROW_TAG = "myCircleRankingRow"
const val MY_CIRCLE_OUTSIDE_ROW_TAG = "myCircleOutsideRow"
const val MY_CIRCLE_RECORD_TAG = "myCircleRecord"

private val RANKING_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)

/** 기록 날짜를 기기 시간대 기준 `2026.11.08` 형식으로 보여준다. */
fun formatRankingDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    RANKING_DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatRank(rank: Int): String = "${rank}위"

val CircleRankingFailure.text: String
    get() = when (this) {
        CircleRankingFailure.NETWORK -> "네트워크에 연결하지 못했어요"
        CircleRankingFailure.AUTH -> "로그인(익명 인증)에 실패했어요"
        CircleRankingFailure.UNKNOWN -> "순위를 불러오지 못했어요"
    }

/** 순위 화면에 서버 순위를 연결한다. */
@Composable
fun CircleRankingRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: CircleRankingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CircleRankingViewModel(context.appContainer.circleRanking) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CircleRankingScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * 왼쪽 조작 영역에 제목, 내 순위·최고 점수와 버튼을 두고, 오른쪽에 상위 10명을 보여준다.
 * 내 기록이 10위 밖이면 순위표 아래에 구분선을 두고 따로 보여준다.
 */
@Composable
fun CircleRankingScreen(
    state: CircleRankingUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DriverSideLayout(
        controls = {
            OverlayCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = CIRCLE_RANKING_TITLE,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    MyRecord(state)
                }
            }
            CarButton(
                text = CIRCLE_RANKING_REFRESH_LABEL,
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                onClick = onBack,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
        },
        content = {
            OverlayCard(modifier = Modifier.fillMaxSize()) {
                Text(text = CIRCLE_RANKING_SUBTITLE, style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RankingList(state)
                }
            }
        },
        modifier = modifier
    )
}

/** 내 순위와 최고 점수. 실패하면 이유를, 기록이 없으면 도전 안내를 보여준다. */
@Composable
private fun MyRecord(state: CircleRankingUiState) {
    Text(text = MY_CIRCLE_RECORD_TITLE, style = MaterialTheme.typography.titleMedium)
    when (state) {
        CircleRankingUiState.Loading -> Message(CIRCLE_RANKING_LOADING)
        is CircleRankingUiState.Failed -> {
            Text(
                text = state.reason.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Message(CIRCLE_RANKING_RETRY_HINT)
        }
        is CircleRankingUiState.Loaded -> {
            val mine = state.board.mine
            if (mine == null) {
                Text(text = MY_CIRCLE_RECORD_EMPTY, style = MaterialTheme.typography.titleMedium)
                Message(MY_CIRCLE_RECORD_EMPTY_BODY)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SDGCornerRadius.BoxRadius.Radius16)
                        .background(SDGColor.Primary50)
                        .padding(16.dp)
                        .testTag(MY_CIRCLE_RECORD_TAG),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = mine.rank?.let(::formatRank) ?: "-",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatScore(mine.score),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Message(formatRankingDate(mine.achievedAtMillis))
                    if (mine.rank == null) {
                        Text(
                            text = MY_CIRCLE_RANK_UNKNOWN,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingList(state: CircleRankingUiState) {
    when (state) {
        CircleRankingUiState.Loading -> Message(CIRCLE_RANKING_LOADING)
        is CircleRankingUiState.Failed -> Message("-")
        is CircleRankingUiState.Loaded -> {
            val board = state.board
            if (board.top.isEmpty()) Message(CIRCLE_RANKING_EMPTY)
            board.top.forEach {
                RankRow(
                    rank = it.rank,
                    nickname = it.nickname,
                    score = it.score,
                    achievedAtMillis = it.achievedAtMillis,
                    mine = it.mine,
                    tag = if (it.mine) MY_CIRCLE_RANKING_ROW_TAG else CIRCLE_RANKING_ROW_TAG
                )
            }
            val mine = board.mine
            if (mine != null && !mine.inTop) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                RankRow(
                    rank = mine.rank,
                    nickname = CIRCLE_RANKING_MINE_LABEL,
                    score = mine.score,
                    achievedAtMillis = mine.achievedAtMillis,
                    mine = true,
                    tag = MY_CIRCLE_OUTSIDE_ROW_TAG
                )
            }
        }
    }
}

/** 순위 한 줄. 내 순위는 강조 배경과 "나" 표시로 구분한다. 순위를 모르면 "-"다. */
@Composable
private fun RankRow(
    rank: Int?,
    nickname: String,
    score: Int,
    achievedAtMillis: Long,
    mine: Boolean,
    tag: String
) {
    val color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(SDGCornerRadius.BoxRadius.Radius12)
            .background(if (mine) SDGColor.Primary50 else SDGColor.Neutral0)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank?.toString() ?: "-",
            style = MaterialTheme.typography.titleLarge,
            color = color,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = nickname,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (mine) {
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
        Text(
            text = formatRankingDate(achievedAtMillis),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatScore(score),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(96.dp)
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
