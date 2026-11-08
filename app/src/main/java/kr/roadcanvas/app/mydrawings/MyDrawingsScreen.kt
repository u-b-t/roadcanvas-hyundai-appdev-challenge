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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import kr.roadcanvas.app.artwork.ArtworkPublishActions
import kr.roadcanvas.app.artwork.ArtworkPublishPanel
import kr.roadcanvas.app.artwork.ArtworkPublishUiState
import kr.roadcanvas.app.artwork.PublishStatus
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.challenge.RETRY_LABEL
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.DrawingNameValidation
import kr.roadcanvas.app.drawing.validateDrawingName
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.displayName
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.CarEmptyState
import kr.roadcanvas.app.ui.components.DriverSideLayout
import kr.roadcanvas.app.ui.components.OVERLAY_CARD_COLOR
import kr.roadcanvas.app.ui.components.OVERLAY_CARD_ELEVATION
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
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
const val RENAME_LABEL = "이름 변경"
const val ADD_NAME_LABEL = "이름 붙이기"
const val DELETE_BLOCKED_BY_PUBLISH = "공개 중인 작품은 비공개로 바꾼 뒤 삭제할 수 있어요"

const val MY_DRAWINGS_LIST_TAG = "myDrawingsList"
const val MY_DRAWINGS_ITEM_SCORE_TAG = "myDrawingsItemScore"
const val MY_DRAWINGS_ITEM_MODE_TAG = "myDrawingsItemMode"
const val DRAWING_DETAIL_TAG = "drawingDetail"
const val DRAWING_DETAIL_OVERLAY_TAG = "drawingDetailOverlay"
const val DRAWING_DETAIL_NAME_TAG = "drawingDetailName"
const val DRAWING_DELETE_BUTTON_TAG = "drawingDeleteButton"

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
            initializer {
                val container = context.appContainer
                MyDrawingsViewModel(
                    container.completedDrawingRepository,
                    container.artworkPublisher
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val publishState by viewModel.publishState.collectAsStateWithLifecycle()

    MyDrawingsScreen(
        state = state,
        onSelect = viewModel::select,
        onRetry = viewModel::retry,
        onRetryDetail = viewModel::retryDetail,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onStartRename = viewModel::startRename,
        onCancelRename = viewModel::cancelRename,
        onSaveName = viewModel::saveName,
        onStartChallenge = onStartChallenge,
        onBack = onBack,
        modifier = modifier,
        publishState = publishState,
        publishActions = ArtworkPublishActions(
            onRequestPublish = viewModel::requestPublish,
            onConfirmPublish = viewModel::confirmPublish,
            onCancelConsent = viewModel::cancelPublishConsent,
            onRetry = viewModel::retryPublish,
            onUnpublish = viewModel::unpublish
        )
    )
}

/**
 * 기록이 있으면 왼쪽 목록과 오른쪽 상세를 나란히 두고, 상세는 경로 지도를 가득 채운 뒤 왼쪽 위에 점수 카드, 왼쪽 아래에 삭제 버튼을 올린다.
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
    modifier: Modifier = Modifier,
    onStartRename: () -> Unit = {},
    onCancelRename: () -> Unit = {},
    onSaveName: (String) -> Unit = {},
    publishState: ArtworkPublishUiState? = null,
    publishActions: ArtworkPublishActions = ArtworkPublishActions()
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
                    publishState = publishState,
                    publishActions = publishActions,
                    onRetryDetail = onRetryDetail,
                    onRequestDelete = onRequestDelete,
                    onStartRename = onStartRename,
                    onCancelRename = onCancelRename,
                    onSaveName = onSaveName,
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

/**
 * 목록 항목의 두 줄 글자. 이름이 있으면 이름과 날짜를, 없으면 날짜와 거리·소요 시간을 보여준다.
 */
fun listItemTexts(summary: CompletedDrawingSummary): Pair<String, String> {
    val date = formatDateTime(summary.completedAtMillis)
    val name = summary.name
    return if (name != null) {
        name to date
    } else {
        date to
            "${formatDistance(summary.distanceMeters)} · ${formatDuration(summary.durationMillis)}"
    }
}

/**
 * SDG 리스트 항목처럼 썸네일, 모드·날짜·거리와 시간 글자와 오른쪽 점수를 한 줄에 둔다.
 * 점수가 없는 자유그림은 점수를 표시하지 않는다. 고른 항목은 Primary 배경으로 표시한다.
 */
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
        RouteThumbnail(points = summary.thumbnail, mode = summary.mode)
        val (title, subtitle) = listItemTexts(summary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.mode.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(MY_DRAWINGS_ITEM_MODE_TAG)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary.mode.scored) {
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
}

/**
 * 상세 영역을 경로 지도로 채우고, 왼쪽 위 오버레이에 점수 카드를 둔다.
 * 삭제 버튼은 점수 카드와 떨어진 왼쪽 아래에 덜 강조된 모양으로 둔다.
 */
@Composable
private fun DrawingDetail(
    state: MyDrawingsUiState,
    publishState: ArtworkPublishUiState?,
    publishActions: ArtworkPublishActions,
    onRetryDetail: () -> Unit,
    onRequestDelete: () -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier) {
        when (val detail = state.detail) {
            is DrawingDetailState.Loaded -> DrawingRouteMap(
                drawing = detail.drawing,
                // 오버레이가 가리는 왼쪽을 지도 콘텐츠 영역에서 빼 경로가 오버레이 오른쪽에 보이게 한다.
                contentPadding = driverSideOverlayContentPadding(),
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
                            // 키보드가 열려 높이가 줄어도 이름 입력과 버튼까지 스크롤해 볼 수 있다.
                            .verticalScroll(rememberScrollState())
                            .testTag(DRAWING_DETAIL_OVERLAY_TAG)
                    ) {
                        DetailOverlayContent(
                            state = state,
                            publishState = publishState,
                            publishActions = publishActions,
                            onRetryDetail = onRetryDetail,
                            onStartRename = onStartRename,
                            onCancelRename = onCancelRename,
                            onSaveName = onSaveName
                        )
                    }
                    // 이름을 바꾸는 동안에는 숨겨 입력과 겹치거나 잘못 누르지 않게 한다.
                    if (state.detail is DrawingDetailState.Loaded && state.rename == null) {
                        DeleteDrawingButton(
                            deleting = state.deleting,
                            blocked = publishState.blocksDelete,
                            onClick = onRequestDelete,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .testTag(DRAWING_DELETE_BUTTON_TAG)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailOverlayContent(
    state: MyDrawingsUiState,
    publishState: ArtworkPublishUiState?,
    publishActions: ArtworkPublishActions,
    onRetryDetail: () -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveName: (String) -> Unit
) {
    when (val detail = state.detail) {
        is DrawingDetailState.Loaded -> if (state.rename != null) {
            RenameEditor(
                drawing = detail.drawing,
                rename = state.rename,
                onCancel = onCancelRename,
                onSave = onSaveName
            )
        } else {
            ScoreCard(drawing = detail.drawing)
            CarButton(
                text = if (detail.drawing.name == null) ADD_NAME_LABEL else RENAME_LABEL,
                onClick = onStartRename,
                enabled = !state.deleting,
                style = CarButtonStyle.Line,
                modifier = Modifier.fillMaxWidth()
            )
            publishState?.let { ArtworkPublishPanel(it, publishActions) }
            if (state.deleteFailed) {
                Text(
                    text = DELETE_FAILED,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (publishState.blocksDelete) {
                Text(
                    text = DELETE_BLOCKED_BY_PUBLISH,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

/** 이름 입력, 저장과 취소 버튼. 입력은 지금 이름으로 시작한다. */
@Composable
private fun RenameEditor(
    drawing: CompletedDrawing,
    rename: RenameState,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    val required = drawing.mode.nameRequired
    val focusManager = LocalFocusManager.current
    var input by rememberSaveable(drawing.id) { mutableStateOf(drawing.name.orEmpty()) }
    var attempted by rememberSaveable(drawing.id) { mutableStateOf(false) }
    DrawingNameInput(
        name = input,
        onNameChange = { input = it },
        required = required,
        showEmptyError = attempted,
        enabled = !rename.saving
    )
    if (rename.failed) {
        Text(
            text = NAME_SAVE_FAILED,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    CarButton(
        text = if (rename.saving) SAVING_NAME_LABEL else SAVE_NAME_LABEL,
        onClick = {
            attempted = true
            // 키보드를 닫아 저장 결과와 숨겼던 버튼이 보이게 한다.
            focusManager.clearFocus()
            if (validateDrawingName(input, required) is DrawingNameValidation.Valid) onSave(input)
        },
        enabled = !rename.saving,
        modifier = Modifier.fillMaxWidth()
    )
    CarButton(
        text = CANCEL_LABEL,
        onClick = onCancel,
        enabled = !rename.saving,
        style = CarButtonStyle.Neutral,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 지도 위에 올리는 작은 삭제 버튼. 실수로 누르지 않도록 채우지 않은 반투명 흰 바탕에 회색 아이콘과 글자로 그리고,
 * 터치 영역은 [MIN_TOUCH_TARGET]을 지킨다.
 */
/**
 * 공개 중이거나 등록 중인 작품은 삭제하지 않는다.
 * 기기 기록을 지우면 기기에서 비공개로 되돌릴 방법이 없어지므로 먼저 비공개로 바꾸게 한다.
 */
private val ArtworkPublishUiState?.blocksDelete: Boolean
    get() = this?.status == PublishStatus.PUBLISHED || this?.status == PublishStatus.PUBLISHING

@Composable
private fun DeleteDrawingButton(
    deleting: Boolean,
    blocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = !deleting && !blocked,
        modifier = modifier.heightIn(min = MIN_TOUCH_TARGET),
        shape = SDGCornerRadius.BoxRadius.Radius16,
        color = OVERLAY_CARD_COLOR,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = OVERLAY_CARD_ELEVATION
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(SdgR.drawable.ic_common_trash),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = if (deleting) DELETING_LABEL else DELETE_LABEL,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** 모드, 이름, 날짜, 큰 점수와 거리·소요 시간을 한 카드에 보여준다. 점수가 없는 자유그림은 점수를 뺀다. */
@Composable
private fun ScoreCard(drawing: CompletedDrawing) {
    Text(
        text = drawing.mode.displayName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    drawing.name?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(DRAWING_DETAIL_NAME_TAG)
        )
    }
    Text(
        text = formatDateTime(drawing.completedAtMillis),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (drawing.mode.scored) {
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
