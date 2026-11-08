package kr.roadcanvas.app.challenge

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.MIN_TOUCH_TARGET
import kr.roadcanvas.app.modeselect.MY_DRAWINGS_LABEL

const val CIRCLE_RESULT_TITLE = "동그라미 챌린지 결과"
const val DURATION_LABEL = "소요 시간"
const val RESULT_CALCULATING = "점수를 계산하고 있어요"
const val RESULT_UNAVAILABLE = "종료한 기록을 불러오지 못했어요"
const val RESULT_SAVING = "결과를 저장하고 있어요"
const val RESULT_SAVED = "결과를 저장했어요"
const val RESULT_SAVE_FAILED = "결과를 저장하지 못했어요"
const val RETRY_SAVE_LABEL = "다시 저장"
const val TRY_AGAIN_LABEL = "다시 도전"
private const val TAG = "ChallengeResult"

/** 결과 화면에 점수 계산과 완료 기록 저장을 연결한다. */
@Composable
fun ChallengeResultRoute(
    startedAtMillis: Long,
    endedAtMillis: Long,
    onTryAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier
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

    ChallengeResultScreen(
        state = state,
        onRetrySave = viewModel::retrySave,
        onTryAgain = onTryAgain,
        onModeSelect = onModeSelect,
        onMyDrawings = onMyDrawings,
        modifier = modifier
    )
}

@Composable
fun ChallengeResultScreen(
    state: ChallengeResultUiState,
    onRetrySave: () -> Unit,
    onTryAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = (state as? ChallengeResultUiState.Calculated)?.result
    Row(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        RouteMap(
            points = result?.path.orEmpty().map { RoutePoint(it.latitude, it.longitude) },
            options = RouteMapOptions(showEndpointMarkers = true, inProgress = false),
            modifier = Modifier.weight(1.4f).fillMaxHeight()
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = CIRCLE_RESULT_TITLE, style = MaterialTheme.typography.headlineMedium)
            when (state) {
                ChallengeResultUiState.Calculating -> StatusText(RESULT_CALCULATING)
                ChallengeResultUiState.Unavailable -> StatusText(RESULT_UNAVAILABLE)
                is ChallengeResultUiState.Calculated -> {
                    ResultSummary(state.result)
                    SaveStatus(state.saveStatus, onRetrySave)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(text = TRY_AGAIN_LABEL, style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onModeSelect,
                    modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)
                ) {
                    Text(
                        text = BACK_TO_MODE_SELECT_LABEL,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutlinedButton(
                    onClick = onMyDrawings,
                    modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)
                ) {
                    Text(text = MY_DRAWINGS_LABEL, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ResultSummary(result: ChallengeResult) {
    Text(text = "${result.score.score}점", style = MaterialTheme.typography.displayMedium)
    result.score.zeroReason?.let {
        Text(
            text = zeroScoreReasonText(it),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Measurement(DISTANCE_LABEL, formatDistance(result.distanceMeters))
        Measurement(DURATION_LABEL, formatElapsed(result.durationMillis))
    }
}

@Composable
private fun SaveStatus(status: ResultSaveStatus, onRetrySave: () -> Unit) {
    when (status) {
        ResultSaveStatus.SAVING -> StatusText(RESULT_SAVING)
        ResultSaveStatus.SAVED -> StatusText(RESULT_SAVED)
        ResultSaveStatus.FAILED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = RESULT_SAVE_FAILED,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onRetrySave,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(text = RETRY_SAVE_LABEL, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
