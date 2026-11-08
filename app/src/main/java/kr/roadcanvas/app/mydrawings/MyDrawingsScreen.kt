package kr.roadcanvas.app.mydrawings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.challenge.RETRY_LABEL
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.MIN_TOUCH_TARGET

const val MY_DRAWINGS_TITLE = "내 그림 목록"
const val MY_DRAWINGS_EMPTY = "아직 저장된 그림이 없어요"
const val START_CIRCLE_CHALLENGE_LABEL = "동그라미 챌린지 시작"
const val MY_DRAWINGS_LOAD_ERROR = "기록을 불러오지 못했어요"
const val DRAWING_DETAIL_LOAD_ERROR = "기록 상세를 불러오지 못했어요"
const val DRAWING_NOT_FOUND = "기록을 찾을 수 없어요"
const val BEST_SCORE_PREFIX = "최고 점수"
const val SCORE_LABEL = "점수"
const val DISTANCE_LABEL = "거리"
const val DURATION_LABEL = "소요 시간"
const val DATE_LABEL = "날짜"
const val DELETE_LABEL = "삭제"
const val DELETING_LABEL = "삭제 중"
const val CANCEL_LABEL = "취소"
const val DELETE_CONFIRM_TITLE = "이 기록을 삭제할까요?"
const val DELETE_CONFIRM_MESSAGE = "삭제한 기록은 되돌릴 수 없어요"
const val DELETE_FAILED = "기록을 삭제하지 못했어요"

const val MY_DRAWINGS_LIST_TAG = "myDrawingsList"
const val MY_DRAWINGS_ITEM_SCORE_TAG = "myDrawingsItemScore"
const val DRAWING_DETAIL_TAG = "drawingDetail"

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
        is DrawingListState.Loaded -> Row(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header(onBack = onBack)
                DrawingList(
                    list = list,
                    selectedId = state.selectedId,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            DrawingDetail(
                state = state,
                onRetryDetail = onRetryDetail,
                onRequestDelete = onRequestDelete,
                modifier = Modifier.weight(3f).fillMaxHeight().testTag(DRAWING_DETAIL_TAG)
            )
        }
        else -> Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
            Header(onBack = onBack)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (list) {
                    DrawingListState.Loading -> CircularProgressIndicator()
                    DrawingListState.Empty -> NoticeWithAction(
                        message = MY_DRAWINGS_EMPTY,
                        actionLabel = START_CIRCLE_CHALLENGE_LABEL,
                        onAction = onStartChallenge
                    )
                    DrawingListState.Error -> NoticeWithAction(
                        message = MY_DRAWINGS_LOAD_ERROR,
                        actionLabel = RETRY_LABEL,
                        onAction = onRetry
                    )
                    is DrawingListState.Loaded -> Unit
                }
            }
        }
    }

    if (state.deleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(text = DELETE_CONFIRM_TITLE) },
            text = { Text(text = DELETE_CONFIRM_MESSAGE) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text(text = DELETE_LABEL) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(text = CANCEL_LABEL) }
            }
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = MY_DRAWINGS_TITLE,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(text = BACK_TO_MODE_SELECT_LABEL, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NoticeWithAction(message: String, actionLabel: String, onAction: () -> Unit) {
    Text(text = message, style = MaterialTheme.typography.headlineSmall)
    Button(
        onClick = onAction,
        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).widthIn(min = 280.dp)
    ) {
        Text(text = actionLabel, style = MaterialTheme.typography.titleLarge)
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        list.best?.let { best ->
            Card(
                onClick = { onSelect(best.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    text = "$BEST_SCORE_PREFIX ${formatScore(best.score)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
private fun DrawingListItem(
    summary: CompletedDrawingSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDateTime(summary.completedAtMillis),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatScore(summary.score),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(MY_DRAWINGS_ITEM_SCORE_TAG)
            )
        }
    }
}

@Composable
private fun DrawingDetail(
    state: MyDrawingsUiState,
    onRetryDetail: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val contentModifier = Modifier.fillMaxWidth().weight(1f)
        when (val detail = state.detail) {
            is DrawingDetailState.Loaded -> {
                DrawingRouteMap(drawing = detail.drawing, modifier = contentModifier)
                DrawingStats(drawing = detail.drawing)
            }
            DrawingDetailState.Error -> Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NoticeWithAction(
                    message = DRAWING_DETAIL_LOAD_ERROR,
                    actionLabel = RETRY_LABEL,
                    onAction = onRetryDetail
                )
            }
            DrawingDetailState.NotFound -> Box(contentModifier, Alignment.Center) {
                Text(text = DRAWING_NOT_FOUND, style = MaterialTheme.typography.headlineSmall)
            }
            DrawingDetailState.Loading, null -> Box(contentModifier, Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.deleteFailed) DELETE_FAILED else "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onRequestDelete,
                enabled = state.detail is DrawingDetailState.Loaded && !state.deleting,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).widthIn(min = 160.dp)
            ) {
                Text(
                    text = if (state.deleting) DELETING_LABEL else DELETE_LABEL,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun DrawingRouteMap(drawing: CompletedDrawing, modifier: Modifier = Modifier) {
    val points = remember(drawing.path) {
        drawing.path.map { RoutePoint(it.latitude, it.longitude) }
    }
    // 지도를 표시할 수 없으면 지도 컴포넌트가 이 영역 안에만 안내를 표시한다.
    RouteMap(points = points, modifier = modifier.clip(MaterialTheme.shapes.medium))
}

@Composable
private fun DrawingStats(drawing: CompletedDrawing) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        listOf(
            SCORE_LABEL to formatScore(drawing.score),
            DISTANCE_LABEL to formatDistance(drawing.distanceMeters),
            DURATION_LABEL to formatDuration(drawing.durationMillis),
            DATE_LABEL to formatDateTime(drawing.completedAtMillis)
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
