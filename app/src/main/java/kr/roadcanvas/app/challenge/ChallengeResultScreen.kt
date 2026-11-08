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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kr.roadcanvas.app.modeselect.MY_DRAWINGS_LABEL
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
    DriverSideOverlay(
        background = {
            // 콘텐츠 여백 안쪽에 전체 경로를 맞춰 오버레이가 경로를 가리지 않게 한다.
            RouteMap(
                points = result?.path.orEmpty().map { RoutePoint(it.latitude, it.longitude) },
                options = RouteMapOptions(
                    showEndpointMarkers = true,
                    inProgress = false,
                    contentPadding = driverSideOverlayContentPadding()
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
                            ResultSummary(state.result)
                            SaveStatus(state.saveStatus, onRetrySave)
                        }
                    }
                }
            }
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
        },
        modifier = modifier
    )
}

@Composable
private fun ResultSummary(result: ChallengeResult) {
    Text(text = "${result.score.score}점", style = MaterialTheme.typography.displayMedium)
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
}

@Composable
private fun SaveStatus(status: ResultSaveStatus, onRetrySave: () -> Unit) {
    when (status) {
        ResultSaveStatus.SAVING -> StatusText(RESULT_SAVING)
        ResultSaveStatus.SAVED -> StatusText(RESULT_SAVED)
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

@Composable
private fun StatusText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
