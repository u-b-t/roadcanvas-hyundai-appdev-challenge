package kr.roadcanvas.app.challenge

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.enums.Keyboard
import com.shopl.sdg_common.util.keyboardAsState
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.DrawingNameValidation
import kr.roadcanvas.app.drawing.normalizeDrawingName
import kr.roadcanvas.app.drawing.validateDrawingName
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.MY_DRAWINGS_LABEL
import kr.roadcanvas.app.mydrawings.DrawingNameInput
import kr.roadcanvas.app.mydrawings.NAME_SAVED
import kr.roadcanvas.app.mydrawings.NAME_SAVE_FAILED
import kr.roadcanvas.app.mydrawings.SAVE_NAME_LABEL
import kr.roadcanvas.app.mydrawings.SAVING_NAME_LABEL
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding

const val CIRCLE_RESULT_TITLE = "동그라미 챌린지 결과"
const val DURATION_LABEL = "소요 시간"
const val RESULT_CALCULATING = "점수를 계산하고 있어요"
const val RESULT_UNAVAILABLE = "종료한 기록을 불러오지 못했어요"
const val RESULT_SAVING = "결과를 저장하고 있어요"
const val RESULT_SAVED = "결과를 저장했어요"
const val RESULT_SAVE_FAILED = "결과를 저장하지 못했어요"
const val RESULT_NOT_SAVED = "이동한 경로가 없어 저장하지 않았어요"
const val RETRY_SAVE_LABEL = "다시 저장"
const val TRY_AGAIN_LABEL = "다시 도전"
const val FIT_CIRCLE_LEGEND = "지도의 주황 원은 점수를 계산한 맞춤 원이에요"
private const val TAG = "ChallengeResult"

/** [mode]의 결과 화면에 결과 계산과 완료 기록 저장을 연결한다. */
@Composable
fun ChallengeResultRoute(
    startedAtMillis: Long,
    endedAtMillis: Long,
    onTryAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier,
    mode: DrawingMode = DrawingMode.CIRCLE
) {
    val context = LocalContext.current
    val viewModel: ChallengeResultViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val container = context.appContainer
                ChallengeResultViewModel(
                    startedAtMillis = startedAtMillis,
                    endedAtMillis = endedAtMillis,
                    savedStateHandle = createSavedStateHandle(),
                    recorder = container.challengeRecorder,
                    inProgressRepository = container.inProgressDrawingRepository,
                    completedRepository = container.completedDrawingRepository,
                    onStorageFailure = { Log.w(TAG, "결과 저장소 작업 실패", it) }
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 진행 화면은 이미 닫혔으므로 뒤로 가면 모드 선택으로 간다.
    BackHandler(onBack = onModeSelect)

    when (mode) {
        DrawingMode.CIRCLE -> ChallengeResultScreen(
            state = state,
            onRetrySave = viewModel::retrySave,
            onSaveName = viewModel::saveName,
            onTryAgain = onTryAgain,
            onModeSelect = onModeSelect,
            onMyDrawings = onMyDrawings,
            modifier = modifier
        )
        DrawingMode.FREE_DRAWING -> FreeDrawingResultScreen(
            state = state,
            onRetrySave = viewModel::retrySave,
            onSaveName = viewModel::saveName,
            onDrawAgain = onTryAgain,
            onModeSelect = onModeSelect,
            onMyDrawings = onMyDrawings,
            modifier = modifier
        )
    }
}

@Composable
fun ChallengeResultScreen(
    state: ChallengeResultUiState,
    onRetrySave: () -> Unit,
    onTryAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveName: (String) -> Unit = {}
) {
    // 키보드가 열리면 이동 버튼을 숨겨 이름 입력창과 이름 저장 버튼이 남은 높이에 보이게 한다.
    val keyboardOpen = keyboardAsState().value == Keyboard.Opened
    val result = (state as? ChallengeResultUiState.Calculated)?.result
    DriverSideOverlay(
        background = {
            // 콘텐츠 여백 안쪽에 전체 경로를 맞춰 오버레이가 경로를 가리지 않게 한다.
            RouteMap(
                points = result?.path.orEmpty().map { RoutePoint(it.latitude, it.longitude) },
                options = RouteMapOptions(
                    showEndpointMarkers = true,
                    inProgress = false,
                    contentPadding = driverSideOverlayContentPadding(),
                    referenceCircle = result?.fitCircle
                ),
                modifier = Modifier.fillMaxSize()
            )
        },
        controls = {
            // 내용이 화면 높이를 넘으면 결과 카드만 스크롤하고 버튼은 항상 보이게 둔다.
            OverlayCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = CIRCLE_RESULT_TITLE, style = MaterialTheme.typography.headlineSmall)
                    when (state) {
                        ChallengeResultUiState.Calculating -> StatusText(RESULT_CALCULATING)
                        ChallengeResultUiState.Unavailable -> StatusText(RESULT_UNAVAILABLE)
                        is ChallengeResultUiState.Calculated -> {
                            ResultSummary(state.result, state.bestComparison)
                            SaveStatus(state.saveStatus, onRetrySave)
                            if (state.saveStatus == ResultSaveStatus.SAVED) {
                                ResultNameEditor(state, onSaveName)
                            }
                        }
                    }
                }
            }
            if (!keyboardOpen) {
                CarButton(
                    text = TRY_AGAIN_LABEL,
                    onClick = onTryAgain,
                    modifier = Modifier.fillMaxWidth()
                )
                CarButton(
                    text = BACK_TO_MODE_SELECT_LABEL,
                    onClick = onModeSelect,
                    style = CarButtonStyle.Neutral,
                    modifier = Modifier.fillMaxWidth()
                )
                CarButton(
                    text = MY_DRAWINGS_LABEL,
                    onClick = onMyDrawings,
                    style = CarButtonStyle.Neutral,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ResultSummary(result: ChallengeResult, bestComparison: BestRecordComparison?) {
    Text(text = "${result.score?.score ?: 0}점", style = MaterialTheme.typography.displayMedium)
    bestComparison?.let {
        Text(
            text = it.text,
            style = MaterialTheme.typography.titleMedium,
            color = if (it is BestRecordComparison.NewRecord) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
    result.zeroReasonText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Measurement(DISTANCE_LABEL, formatDistance(result.distanceMeters))
        Measurement(DURATION_LABEL, formatElapsed(result.durationMillis))
    }
    ScoreBreakdown(result)
}

/** 점수 구성 항목별 비율과 가장 낮은 항목의 고칠 점. 0점이면 항목이 없어 그리지 않는다. */
@Composable
private fun ScoreBreakdown(result: ChallengeResult) {
    val components = result.scoreComponents
    if (components.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        components.forEach { ScoreComponentRow(it) }
        result.improvementTip?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = FIT_CIRCLE_LEGEND,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreComponentRow(component: ScoreComponent) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = component.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(text = "${component.percent}%", style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { component.ratio.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {}
        )
    }
}

@Composable
internal fun SaveStatus(status: ResultSaveStatus, onRetrySave: () -> Unit) {
    when (status) {
        ResultSaveStatus.SAVING -> StatusText(RESULT_SAVING)
        ResultSaveStatus.SAVED -> StatusText(RESULT_SAVED)
        ResultSaveStatus.NOT_SAVED -> StatusText(RESULT_NOT_SAVED)
        ResultSaveStatus.FAILED -> {
            Text(
                text = RESULT_SAVE_FAILED,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            CarButton(
                text = RETRY_SAVE_LABEL,
                onClick = onRetrySave,
                style = CarButtonStyle.Line,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 저장한 결과에 이름을 붙이는 입력창과 저장 버튼. 이름이 선택 입력인 모드는 비워 두고 넘어갈 수 있다. */
@Composable
internal fun ResultNameEditor(
    state: ChallengeResultUiState.Calculated,
    onSaveName: (String) -> Unit
) {
    val required = state.result.mode.nameRequired
    val focusManager = LocalFocusManager.current
    // 저장한 이름이 바뀌면 입력창도 앞뒤 공백을 제거한 저장 값으로 맞춘다.
    var input by rememberSaveable(state.name) { mutableStateOf(state.name.orEmpty()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val saving = state.nameSaveStatus == NameSaveStatus.SAVING
    DrawingNameInput(
        name = input,
        onNameChange = { input = it },
        required = required,
        showEmptyError = attempted,
        enabled = !saving
    )
    CarButton(
        text = if (saving) SAVING_NAME_LABEL else SAVE_NAME_LABEL,
        onClick = {
            attempted = true
            // 키보드를 닫아 저장 결과와 숨겼던 버튼이 보이게 한다.
            focusManager.clearFocus()
            if (validateDrawingName(input, required) is DrawingNameValidation.Valid) {
                onSaveName(input)
            }
        },
        enabled = !saving,
        style = CarButtonStyle.Line,
        modifier = Modifier.fillMaxWidth()
    )
    when (state.nameSaveStatus) {
        NameSaveStatus.SAVED -> if (normalizeDrawingName(input) == state.name.orEmpty()) {
            StatusText(NAME_SAVED)
        }
        NameSaveStatus.FAILED -> Text(
            text = NAME_SAVE_FAILED,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        NameSaveStatus.IDLE, NameSaveStatus.SAVING -> Unit
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
