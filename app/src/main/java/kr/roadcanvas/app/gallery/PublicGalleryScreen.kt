package kr.roadcanvas.app.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import com.shopl.sdg_resource.R as SdgR
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.challenge.RETRY_LABEL
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.mydrawings.ModeThumbnail
import kr.roadcanvas.app.mydrawings.RouteThumbnail
import kr.roadcanvas.app.mydrawings.formatDistance
import kr.roadcanvas.app.mydrawings.formatDuration
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.CarEmptyState
import kr.roadcanvas.app.ui.components.DriverSideLayout
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val GALLERY_TITLE = "공개 작품"
const val GALLERY_EMPTY = "아직 공개된 작품이 없어요"
const val GALLERY_EMPTY_BODY = "이름 붙인 자유그림을 공개하면 여기에 보여요"
const val GALLERY_LOAD_ERROR = "공개 작품을 불러오지 못했어요"
const val GALLERY_REFRESH_LABEL = "새로고침"
const val GALLERY_LOAD_MORE_LABEL = "더 보기"
const val GALLERY_LOAD_MORE_ERROR = "다음 작품을 불러오지 못했어요"
const val GALLERY_VOTES_LOADING = "표 수 확인 중"
const val GALLERY_VOTES_FAILED = "표 수 확인 실패"

const val GALLERY_LIST_TAG = "galleryList"
const val GALLERY_ITEM_VOTES_TAG = "galleryItemVotes"

/** 공개 작품 목록 화면에 서버 저장소를 연결한다. */
@Composable
fun PublicGalleryRoute(
    onOpenArtwork: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: PublicGalleryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PublicGalleryViewModel(context.appContainer.publicGallery) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    PublicGalleryScreen(
        state = state,
        onItemVisible = viewModel::onItemVisible,
        onOpenArtwork = onOpenArtwork,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onBack = onBack,
        modifier = modifier
    )
}

/** 실패 이유별 다음 행동 안내 */
fun galleryFailureHint(reason: GalleryFailure): String = when (reason) {
    GalleryFailure.NETWORK -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요"
    GalleryFailure.AUTH -> "계정을 확인하지 못했어요. 잠시 후 다시 시도해 주세요"
    GalleryFailure.NOT_PUBLISHED -> "작성자가 공개를 취소해 볼 수 없어요"
    GalleryFailure.INVALID_ROUTE -> "저장된 경로가 올바르지 않아요"
    GalleryFailure.UNKNOWN -> "잠시 후 다시 시도해 주세요"
}

/** 표 수 표시. 아직 모르면 확인 중, 읽지 못했으면 실패로 보인다. */
fun formatVotes(extras: ArtworkItemExtras?): String = when {
    extras?.votes != null -> formatVoteCount(extras.votes)
    extras?.votesFailed == true -> GALLERY_VOTES_FAILED
    else -> GALLERY_VOTES_LOADING
}

fun formatVoteCount(votes: Long): String = "${votes}표"

/**
 * 운전석 쪽 왼쪽에 뒤로 가기·새로고침 버튼을, 오른쪽에 작품 목록을 둔다.
 * 목록이 비었거나 불러오지 못하면 오른쪽에 안내를 둔다.
 */
@Composable
fun PublicGalleryScreen(
    state: PublicGalleryUiState,
    onItemVisible: (PublicArtwork) -> Unit,
    onOpenArtwork: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list = state.list
    DriverSideLayout(
        modifier = modifier,
        controls = {
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                onClick = onBack,
                style = CarButtonStyle.Line
            )
            Text(
                text = GALLERY_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            CarButton(
                text = if (list is GalleryListState.Error) RETRY_LABEL else GALLERY_REFRESH_LABEL,
                onClick = onRefresh,
                enabled = list != GalleryListState.Loading,
                style = if (list is GalleryListState.Error) {
                    CarButtonStyle.Primary
                } else {
                    CarButtonStyle.Neutral
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        content = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (list) {
                    GalleryListState.Loading -> CircularProgressIndicator()
                    GalleryListState.Empty -> CarEmptyState(
                        title = GALLERY_EMPTY,
                        body = GALLERY_EMPTY_BODY
                    ) {
                        ModeThumbnail(
                            mode = DrawingMode.FREE_DRAWING,
                            size = 112.dp,
                            backgroundColor = null
                        )
                    }
                    is GalleryListState.Error -> CarEmptyState(
                        title = GALLERY_LOAD_ERROR,
                        body = galleryFailureHint(list.reason),
                        illustrationBackground = SDGColor.Neutral150
                    ) {
                        Icon(
                            painter = painterResource(SdgR.drawable.ic_common_warning),
                            contentDescription = null,
                            tint = SDGColor.Neutral400,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                    is GalleryListState.Loaded -> ArtworkList(
                        list = list,
                        extras = state.extras,
                        onItemVisible = onItemVisible,
                        onOpenArtwork = onOpenArtwork,
                        onLoadMore = onLoadMore
                    )
                }
            }
        }
    )
}

@Composable
private fun ArtworkList(
    list: GalleryListState.Loaded,
    extras: Map<String, ArtworkItemExtras>,
    onItemVisible: (PublicArtwork) -> Unit,
    onOpenArtwork: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(GALLERY_LIST_TAG),
        verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
    ) {
        items(list.items, key = { it.id }) { artwork ->
            // LazyColumn은 화면에 보이는 항목만 구성하므로 이때 표 수와 썸네일을 요청한다.
            LaunchedEffect(artwork.id) { onItemVisible(artwork) }
            ArtworkListItem(
                artwork = artwork,
                extras = extras[artwork.id],
                onClick = { onOpenArtwork(artwork.id) }
            )
        }
        if (list.hasMore || list.loadMoreFailure != null) {
            item(key = "loadMore") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.loadMoreFailure?.let {
                        Text(
                            text = GALLERY_LOAD_MORE_ERROR,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    CarButton(
                        text = if (list.loadMoreFailure !=
                            null
                        ) {
                            RETRY_LABEL
                        } else {
                            GALLERY_LOAD_MORE_LABEL
                        },
                        onClick = onLoadMore,
                        enabled = !list.loadingMore,
                        style = CarButtonStyle.Neutral,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** 경로 썸네일, 작품 이름, 닉네임, 거리·시간과 오른쪽 표 수를 한 줄에 둔다. */
@Composable
private fun ArtworkListItem(
    artwork: PublicArtwork,
    extras: ArtworkItemExtras?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        shape = SDGCornerRadius.BoxRadius.Radius16,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 경로를 읽기 전이나 읽지 못하면 자유그림 그림을 대신 그린다.
            RouteThumbnail(
                points = extras?.thumbnail.orEmpty(),
                mode = DrawingMode.FREE_DRAWING,
                size = 72.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artwork.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artwork.nickname,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDistance(artwork.distanceMeters)} · " +
                        formatDuration(artwork.durationMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatVotes(extras),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(GALLERY_ITEM_VOTES_TAG)
            )
        }
    }
}
