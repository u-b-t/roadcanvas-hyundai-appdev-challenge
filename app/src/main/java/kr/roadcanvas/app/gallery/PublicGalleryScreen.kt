package kr.roadcanvas.app.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.challenge.RETRY_LABEL
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapNotice
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint as MapPoint
import kr.roadcanvas.app.mydrawings.RouteThumbnail
import kr.roadcanvas.app.mydrawings.formatDistance
import kr.roadcanvas.app.mydrawings.formatDuration
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
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
const val GALLERY_OPEN_LABEL = "작품 보기"

const val GALLERY_LIST_TAG = "galleryList"
const val GALLERY_ITEM_VOTES_TAG = "galleryItemVotes"
const val GALLERY_OVERLAY_TAG = "galleryOverlay"
const val GALLERY_SELECTED_ITEM_TAG = "gallerySelectedItem"

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
        onSelectArtwork = viewModel::select,
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
 * 고른 작품의 경로 지도를 화면 전체에 깔고 운전석 쪽 왼쪽 오버레이에 작품 목록을 둔다.
 * 항목을 누르면 그 작품을 골라 지도에 경로를 그리고, 고른 항목의 작품 보기 버튼으로 상세에 들어간다.
 * 목록이 비었거나 불러오지 못하면 오버레이에 안내를 둔다. 새로고침과 모드 선택으로 버튼은 오버레이 아래에 항상 보이게 둔다.
 */
@Composable
fun PublicGalleryScreen(
    state: PublicGalleryUiState,
    onItemVisible: (PublicArtwork) -> Unit,
    onSelectArtwork: (PublicArtwork) -> Unit,
    onOpenArtwork: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list = state.list
    DriverSideOverlay(
        modifier = modifier,
        background = { SelectedRouteMap(state.selected) },
        controls = {
            // 목록이 길면 카드 안에서만 스크롤하고 버튼은 항상 보이게 둔다.
            OverlayCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag(GALLERY_OVERLAY_TAG)
            ) {
                Text(text = GALLERY_TITLE, style = MaterialTheme.typography.headlineSmall)
                when (list) {
                    GalleryListState.Loading -> CircularProgressIndicator()
                    GalleryListState.Empty -> Notice(GALLERY_EMPTY, GALLERY_EMPTY_BODY)
                    is GalleryListState.Error -> Notice(
                        title = GALLERY_LOAD_ERROR,
                        body = galleryFailureHint(list.reason),
                        titleColor = MaterialTheme.colorScheme.error
                    )
                    is GalleryListState.Loaded -> ArtworkList(
                        list = list,
                        extras = state.extras,
                        selectedId = state.selected?.id,
                        onItemVisible = onItemVisible,
                        onSelectArtwork = onSelectArtwork,
                        onOpenArtwork = onOpenArtwork,
                        onLoadMore = onLoadMore,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            CarButton(
                text = if (list is GalleryListState.Error) RETRY_LABEL else GALLERY_REFRESH_LABEL,
                onClick = onRefresh,
                enabled = list != GalleryListState.Loading,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                onClick = onBack,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

/** 고른 작품의 경로 지도. 고르기 전이나 경로를 읽는 중이면 빈 배경, 읽지 못하면 이유를 지도 영역에 보인다. */
@Composable
private fun SelectedRouteMap(selected: SelectedArtwork?) {
    val route = selected?.route
    if (route != null) {
        val points = remember(route) { route.map { MapPoint(it.latitude, it.longitude) } }
        RouteMap(
            points = points,
            options = RouteMapOptions(contentPadding = driverSideOverlayContentPadding()),
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SDGColor.Neutral100)
            .padding(driverSideOverlayContentPadding()),
        contentAlignment = Alignment.Center
    ) {
        selected?.failure?.let {
            val text = if (it ==
                GalleryFailure.INVALID_ROUTE
            ) {
                ARTWORK_INVALID_ROUTE
            } else {
                ARTWORK_LOAD_ERROR
            }
            RouteMapNotice(text)
        }
    }
}

@Composable
private fun Notice(
    title: String,
    body: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ArtworkList(
    list: GalleryListState.Loaded,
    extras: Map<String, ArtworkItemExtras>,
    selectedId: String?,
    onItemVisible: (PublicArtwork) -> Unit,
    onSelectArtwork: (PublicArtwork) -> Unit,
    onOpenArtwork: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag(GALLERY_LIST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(list.items, key = { it.id }) { artwork ->
            // LazyColumn은 화면에 보이는 항목만 구성하므로 이때 표 수와 썸네일을 요청한다.
            LaunchedEffect(artwork.id) { onItemVisible(artwork) }
            ArtworkListItem(
                artwork = artwork,
                extras = extras[artwork.id],
                selected = artwork.id == selectedId,
                onClick = { onSelectArtwork(artwork) },
                onOpen = { onOpenArtwork(artwork.id) }
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

/**
 * 경로 썸네일, 작품 이름, 닉네임, 거리·시간과 오른쪽 표 수를 한 줄에 둔다.
 * 고른 항목은 강조 배경과 테두리로 구분하고 아래에 작품 보기 버튼을 둔다.
 */
@Composable
private fun ArtworkListItem(
    artwork: PublicArtwork,
    extras: ArtworkItemExtras?,
    selected: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .then(if (selected) Modifier.testTag(GALLERY_SELECTED_ITEM_TAG) else Modifier),
        shape = SDGCornerRadius.BoxRadius.Radius16,
        color = if (selected) SDGColor.Primary50 else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 경로를 읽기 전이나 읽지 못하면 자유그림 그림을 대신 그린다.
                RouteThumbnail(
                    points = extras?.thumbnail.orEmpty(),
                    mode = DrawingMode.FREE_DRAWING,
                    size = 56.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artwork.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artwork.nickname,
                        style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(GALLERY_ITEM_VOTES_TAG)
                )
            }
            if (selected) {
                CarButton(
                    text = GALLERY_OPEN_LABEL,
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
