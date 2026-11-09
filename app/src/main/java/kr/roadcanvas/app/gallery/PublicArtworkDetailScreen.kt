package kr.roadcanvas.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.RETRY_LABEL
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.mydrawings.DISTANCE_LABEL
import kr.roadcanvas.app.mydrawings.DURATION_LABEL
import kr.roadcanvas.app.mydrawings.formatDistance
import kr.roadcanvas.app.mydrawings.formatDuration
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val ARTWORK_BACK_LABEL = "목록으로"
const val ARTWORK_LOAD_ERROR = "작품을 불러오지 못했어요"
const val ARTWORK_INVALID_ROUTE = "경로를 불러올 수 없는 작품이에요"
const val ARTWORK_NOT_PUBLISHED = "비공개로 전환된 작품이에요"
const val VOTES_LABEL = "표 수"
const val VOTES_UNKNOWN = "표 수를 불러오지 못했어요"
const val VOTE_LABEL = "투표하기"
const val UNVOTE_LABEL = "투표 취소"
const val VOTING_LABEL = "처리 중"
const val VOTED_MESSAGE = "투표했어요"
const val VOTE_FAILED = "투표하지 못했어요"
const val UNVOTE_FAILED = "투표를 취소하지 못했어요"

const val ARTWORK_DETAIL_TAG = "artworkDetail"
const val ARTWORK_DETAIL_OVERLAY_TAG = "artworkDetailOverlay"
const val ARTWORK_DETAIL_VOTES_TAG = "artworkDetailVotes"

/** 공개 작품 상세 화면에 서버 저장소를 연결한다. */
@Composable
fun PublicArtworkDetailRoute(artworkId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: PublicArtworkDetailViewModel = viewModel(
        key = artworkId,
        factory = viewModelFactory {
            initializer {
                PublicArtworkDetailViewModel(context.appContainer.publicGallery, artworkId)
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    PublicArtworkDetailScreen(
        state = state,
        onToggleVote = viewModel::toggleVote,
        onRetry = viewModel::retry,
        onBack = onBack,
        modifier = modifier
    )
}

/** 경로 지도를 화면 전체에 깔고 운전석 쪽 왼쪽 오버레이에 작품 정보와 투표 버튼을 둔다. */
@Composable
fun PublicArtworkDetailScreen(
    state: ArtworkDetailState,
    onToggleVote: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DriverSideOverlay(
        modifier = modifier.testTag(ARTWORK_DETAIL_TAG),
        background = {
            if (state is ArtworkDetailState.Loaded) {
                val points = remember(state.detail.route) {
                    state.detail.route.map { RoutePoint(it.latitude, it.longitude) }
                }
                // 지도를 표시할 수 없으면 지도 컴포넌트가 콘텐츠 영역 안에 안내를 표시한다.
                RouteMap(
                    points = points,
                    options = RouteMapOptions(
                        showEndpointMarkers = true,
                        inProgress = false,
                        contentPadding = driverSideOverlayContentPadding()
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(SDGColor.Neutral100))
            }
        },
        controls = {
            // 내용이 화면 높이를 넘으면 카드만 스크롤하고 목록으로 버튼은 항상 보이게 둔다.
            CarButton(text = ARTWORK_BACK_LABEL, onClick = onBack, style = CarButtonStyle.Line)
            OverlayCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag(ARTWORK_DETAIL_OVERLAY_TAG)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailContent(state = state, onToggleVote = onToggleVote, onRetry = onRetry)
                }
            }
        }
    )
}

@Composable
private fun DetailContent(
    state: ArtworkDetailState,
    onToggleVote: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        ArtworkDetailState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        ArtworkDetailState.NotPublished -> {
            Text(text = ARTWORK_NOT_PUBLISHED, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = galleryFailureHint(GalleryFailure.NOT_PUBLISHED),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is ArtworkDetailState.Error -> {
            Text(
                text = if (state.reason == GalleryFailure.INVALID_ROUTE) {
                    ARTWORK_INVALID_ROUTE
                } else {
                    ARTWORK_LOAD_ERROR
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = galleryFailureHint(state.reason),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CarButton(text = RETRY_LABEL, onClick = onRetry, modifier = Modifier.fillMaxWidth())
        }
        is ArtworkDetailState.Loaded -> LoadedContent(state, onToggleVote)
    }
}

@Composable
private fun LoadedContent(state: ArtworkDetailState.Loaded, onToggleVote: () -> Unit) {
    val detail = state.detail
    Text(
        text = detail.artwork.title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = detail.artwork.nickname,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            DISTANCE_LABEL to formatDistance(detail.artwork.distanceMeters),
            DURATION_LABEL to formatDuration(detail.artwork.durationMillis)
        ).forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = value, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
    Column {
        Text(
            text = VOTES_LABEL,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = detail.votes?.let(::formatVoteCount) ?: VOTES_UNKNOWN,
            style = if (detail.votes != null) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(ARTWORK_DETAIL_VOTES_TAG)
        )
    }
    val voted = detail.voted == true
    if (voted) {
        Text(
            text = VOTED_MESSAGE,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    state.voteFailure?.let { reason ->
        Text(
            text = "${if (voted) UNVOTE_FAILED else VOTE_FAILED}. ${galleryFailureHint(reason)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    CarButton(
        text = when {
            state.voting -> VOTING_LABEL
            voted -> UNVOTE_LABEL
            else -> VOTE_LABEL
        },
        onClick = onToggleVote,
        enabled = !state.voting,
        style = if (voted) CarButtonStyle.Line else CarButtonStyle.Primary,
        modifier = Modifier.fillMaxWidth()
    )
}
