package kr.roadcanvas.app.mydrawings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
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
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.CarEmptyState
import kr.roadcanvas.app.ui.components.DriverSideLayout
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.theme.CONTROL_PANEL_WIDTH
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.ui.theme.SCREEN_PADDING

const val MY_DRAWINGS_TITLE = "내 그림 목록"
const val MY_DRAWINGS_EMPTY = "아직 저장된 그림이 없어요"
const val MY_DRAWINGS_EMPTY_BODY = "동그라미 챌린지로 첫 그림을 그려 보세요"
const val START_CIRCLE_CHALLENGE_LABEL = "동그라미 챌린지 시작"
const val MY_DRAWINGS_LOAD_ERROR = "기록을 불러오지 못했어요"
const val DRAWING_DETAIL_LOAD_ERROR = "기록 상세를 불러오지 못했어요"
const val DRAWING_NOT_FOUND = "기록을 찾을 수 없어요"
const val BEST_SCORE_PREFIX = "최고 점수"
const val SCORE_LABEL = "점수"
const val DISTANCE_LABEL = "거리"
const val DURATION_LABEL = "소요 시간"
const val DELETE_LABEL = "삭제"
const val DELETING_LABEL = "삭제 중"
const val CANCEL_LABEL = "취소"
const val DELETE_CONFIRM_TITLE = "이 기록을 삭제할까요?"
const val DELETE_CONFIRM_MESSAGE = "삭제한 기록은 되돌릴 수 없어요"
const val DELETE_FAILED = "기록을 삭제하지 못했어요"

const val MY_DRAWINGS_LIST_TAG = "myDrawingsList"
const val MY_DRAWINGS_ITEM_SCORE_TAG = "myDrawingsItemScore"
const val DRAWING_DETAIL_TAG = "drawingDetail"
const val DRAWING_DETAIL_OVERLAY_TAG = "drawingDetailOverlay"

/** 운전석 쪽 목록 영역의 너비. 썸네일, 날짜와 점수가 한 줄에 들어가도록 조작 영역보다 넓다. */
private val LIST_PANEL_WIDTH = 440.dp

/** 내 그림 화면에 완료 기록 저장소를 연결한다. */
@Composable
fun MyDrawingsRoute(
    onStartChallenge: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MyDrawingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MyDrawingsViewModel(context.appContainer.completedDrawingRepository) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    MyDrawingsScreen(
        state = state,
        onSelect = viewModel::select,
        onRetry = viewModel::retry,
        onRetryDetail = viewModel::retryDetail,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onStartChallenge = onStartChallenge,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * 기록이 있으면 왼쪽 목록과 오른쪽 상세를 나란히 두고, 상세는 경로 지도를 가득 채운 뒤 왼쪽에 점수 카드와 삭제 버튼을 올린다.
 * 기록이 없거나 불러오지 못하면 왼쪽 조작 영역에 버튼을, 오른쪽에 SDG 빈 상태 안내를 둔다.
 */
@Composable
fun MyDrawingsScreen(
    state: MyDrawingsUiState,
    onSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onRetryDetail: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onStartChallenge: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (val list = state.list) {
        is DrawingListState.Loaded -> ListAndDetail(
            list = {
                Header(onBack = onBack)
                DrawingList(
                    list = list,
                    selectedId = state.selectedId,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            },
            detail = {
                DrawingDetail(
                    state = state,
                    onRetryDetail = onRetryDetail,
                    onRequestDelete = onRequestDelete,
                    modifier = Modifier.fillMaxSize().testTag(DRAWING_DETAIL_TAG)
                )
            },
            modifier = modifier
        )
        else -> DriverSideLayout(
            modifier = modifier,
            controls = {
                Header(onBack = onBack)
                when (list) {
                    DrawingListState.Empty -> CarButton(
                        text = START_CIRCLE_CHALLENGE_LABEL,
                        onClick = onStartChallenge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DrawingListState.Error -> CarButton(
                        text = RETRY_LABEL,
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> Unit
                }
            },
            content = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (list) {
                        DrawingListState.Loading -> CircularProgressIndicator()
                        DrawingListState.Empty -> CarEmptyState(
                            title = MY_DRAWINGS_EMPTY,
                            body = MY_DRAWINGS_EMPTY_BODY
                        ) {
                            ModeThumbnail(
                                mode = DrawingMode.CIRCLE,
                                size = 112.dp,
                                backgroundColor = null
                            )
                        }
                        DrawingListState.Error -> ErrorState(message = MY_DRAWINGS_LOAD_ERROR)
                        is DrawingListState.Loaded -> Unit
                    }
                }
            }
        )
    }

    if (state.deleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            shape = SDGCornerRadius.BoxRadius.Radius20,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(text = DELETE_CONFIRM_TITLE, style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    text = DELETE_CONFIRM_MESSAGE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                CarButton(text = DELETE_LABEL, onClick = onConfirmDelete)
            },
            dismissButton = {
                CarButton(
                    text = CANCEL_LABEL,
                    onClick = onCancelDelete,
                    style = CarButtonStyle.Neutral
                )
            }
        )
    }
}

/**
 * 운전석 쪽 왼쪽에 [list]를, 나머지 영역에 [detail]을 여백 없이 둔다.
 * 오른쪽에서 왼쪽으로 쓰는 언어 설정이어도 목록은 왼쪽에 둔다.
 */
@Composable
private fun ListAndDetail(
    list: @Composable ColumnScope.() -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Column(
                    modifier = Modifier
                        .width(LIST_PANEL_WIDTH)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(SCREEN_PADDING),
                    verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
                    content = list
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { detail() }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    CarButton(text = BACK_TO_MODE_SELECT_LABEL, onClick = onBack, style = CarButtonStyle.Line)
    Text(
        text = MY_DRAWINGS_TITLE,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    CarEmptyState(
        title = message,
        modifier = modifier,
        illustrationBackground = SDGColor.Neutral150
    ) {
        Icon(
            painter = painterResource(SdgR.drawable.ic_common_warning),
            contentDescription = null,
            tint = SDGColor.Neutral400,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun DrawingList(
    list: DrawingListState.Loaded,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag(MY_DRAWINGS_LIST_TAG),
        verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
    ) {
        list.best?.let { best ->
            Surface(
                onClick = { onSelect(best.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                shape = SDGCornerRadius.BoxRadius.Radius16,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "$BEST_SCORE_PREFIX ${formatScore(best.score)}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(list.items, key = { it.id }) { summary ->
                DrawingListItem(
                    summary = summary,
                    selected = summary.id == selectedId,
                    onClick = { onSelect(summary.id) }
                )
            }
        }
    }
}

/** SDG 리스트 항목처럼 썸네일, 두 줄 글자와 오른쪽 값을 한 줄에 둔다. 고른 항목은 Primary 배경으로 표시한다. */
@Composable
private fun DrawingListItem(
    summary: CompletedDrawingSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(SDGCornerRadius.BoxRadius.Radius16)
            .background(if (selected) SDGColor.Primary50 else SDGColor.Neutral0)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeThumbnail(mode = summary.mode)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDateTime(summary.completedAtMillis),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatDistance(summary.distanceMeters)} · " +
                    formatDuration(summary.durationMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatScore(summary.score),
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.testTag(MY_DRAWINGS_ITEM_SCORE_TAG)
        )
    }
}

/** 상세 영역을 경로 지도로 채우고, 왼쪽 위 오버레이에 점수 카드와 삭제 버튼을 둔다. */
@Composable
private fun DrawingDetail(
    state: MyDrawingsUiState,
    onRetryDetail: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier) {
        when (val detail = state.detail) {
            is DrawingDetailState.Loaded -> DrawingRouteMap(
                drawing = detail.drawing,
                // 오버레이가 가리는 왼쪽을 지도 콘텐츠 영역에서 빼 경로가 오버레이 오른쪽에 보이게 한다.
                contentPadding = PaddingValues(start = SCREEN_PADDING * 2 + CONTROL_PANEL_WIDTH),
                modifier = Modifier.fillMaxSize()
            )
            else -> Box(Modifier.fillMaxSize().background(SDGColor.Neutral100))
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING)) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    OverlayCard(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(CONTROL_PANEL_WIDTH)
                            .testTag(DRAWING_DETAIL_OVERLAY_TAG)
                    ) {
                        DetailOverlayContent(state, onRetryDetail, onRequestDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailOverlayContent(
    state: MyDrawingsUiState,
    onRetryDetail: () -> Unit,
    onRequestDelete: () -> Unit
) {
    when (val detail = state.detail) {
        is DrawingDetailState.Loaded -> {
            ScoreCard(drawing = detail.drawing)
            if (state.deleteFailed) {
                Text(
                    text = DELETE_FAILED,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            CarButton(
                text = if (state.deleting) DELETING_LABEL else DELETE_LABEL,
                onClick = onRequestDelete,
                enabled = !state.deleting,
                style = CarButtonStyle.Line,
                modifier = Modifier.fillMaxWidth()
            )
        }
        DrawingDetailState.Error -> {
            Text(text = DRAWING_DETAIL_LOAD_ERROR, style = MaterialTheme.typography.titleLarge)
            CarButton(
                text = RETRY_LABEL,
                onClick = onRetryDetail,
                modifier = Modifier.fillMaxWidth()
            )
        }
        DrawingDetailState.NotFound ->
            Text(text = DRAWING_NOT_FOUND, style = MaterialTheme.typography.titleLarge)
        DrawingDetailState.Loading, null -> Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/** 날짜, 큰 점수와 거리·소요 시간을 한 카드에 보여준다. */
@Composable
private fun ScoreCard(drawing: CompletedDrawing) {
    Text(
        text = formatDateTime(drawing.completedAtMillis),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column {
        Text(
            text = SCORE_LABEL,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatScore(drawing.score),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            DISTANCE_LABEL to formatDistance(drawing.distanceMeters),
            DURATION_LABEL to formatDuration(drawing.durationMillis)
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
}

@Composable
private fun DrawingRouteMap(
    drawing: CompletedDrawing,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val points = remember(drawing.path) {
        drawing.path.map { RoutePoint(it.latitude, it.longitude) }
    }
    // 지도를 표시할 수 없으면 지도 컴포넌트가 콘텐츠 영역 안에 안내를 표시한다.
    RouteMap(
        points = points,
        options = RouteMapOptions(contentPadding = contentPadding),
        modifier = modifier
    )
}
