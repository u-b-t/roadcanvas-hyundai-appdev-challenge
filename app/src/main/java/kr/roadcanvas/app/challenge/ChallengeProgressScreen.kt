package kr.roadcanvas.app.challenge

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.map.CurrentPosition
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.recording.RecordingPermissions
import kr.roadcanvas.app.recording.RecordingRequest
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding

const val CIRCLE_PROGRESS_TITLE = "동그라미 챌린지 진행"
const val ELAPSED_LABEL = "경과 시간"
const val DISTANCE_LABEL = "이동 거리"
const val DISTANCE_TO_START_LABEL = "출발점까지"
const val TURN_PROGRESS_LABEL = "회전 진행률"
const val FINISH_GUIDANCE = "출발점 도착! 정차 후 종료하세요"
const val FINISH_LABEL = "종료"
const val GIVE_UP_LABEL = "포기"
const val PROGRESS_PREPARING = "기록을 준비하고 있어요"
const val PROGRESS_FAILED = "기록을 시작하지 못했어요"
const val BACK_TO_READY_LABEL = "준비 화면으로"
const val CONTROL_RESTRICTED_REASON = "정차하면 종료할 수 있어요"
const val CONTROL_UNKNOWN_REASON = "주행 상태 확인 불가"
const val GIVE_UP_CONFIRM_TITLE = "도전을 포기할까요?"
const val GIVE_UP_CONFIRM_MESSAGE = "지금까지 기록한 경로를 지우고 저장하지 않아요"
const val GIVE_UP_CONFIRM_LABEL = "포기하기"
const val GIVE_UP_CANCEL_LABEL = "계속하기"
const val GIVE_UP_FAILED_MESSAGE = "기록을 지우지 못했어요. 기록은 계속돼요"
private const val CONFIRM_LABEL = "확인"

/**
 * 준비 화면의 시작 버튼에 연결할 동작. 기록 서비스에 필요한 권한을 요청한 뒤 새 기록을 실행하고
 * [onStarted]로 진행 화면에 넘어간다. 권한을 거절하면 서비스가 시작하지 못해 진행 화면이 실패를 안내한다.
 */
@Composable
fun rememberChallengeStarter(onStarted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnStarted by rememberUpdatedState(onStarted)
    val start = remember(context) {
        {
            context.appContainer.recordingRunner.run(RecordingRequest.New(DrawingMode.CIRCLE))
            currentOnStarted()
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { start() }
    return remember(context, launcher) {
        {
            val missing = RecordingPermissions.runtime().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) start() else launcher.launch(missing.toTypedArray())
        }
    }
}

/** 진행 화면에 기록 상태 구독과 종료·포기를 연결한다. */
@Composable
fun ChallengeProgressRoute(
    onFinished: (RecordingState.Finished) -> Unit,
    onGaveUp: () -> Unit,
    onBackToReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ChallengeProgressViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val container = context.appContainer
                ChallengeProgressViewModel(
                    container.challengeRecorder,
                    container.recordingRunner,
                    container.drivingRestrictionProvider
                )
            }
        }
    )
    // 화면이 멈추면 구독자가 없어져 화면 갱신도 멈춘다. 기록은 서비스에서 계속된다.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val giveUpFailed by viewModel.giveUpFailed.collectAsStateWithLifecycle()

    // 도전 중에는 종료나 포기로만 화면을 떠난다.
    BackHandler(enabled = state.phase != ChallengeProgressPhase.FAILED) {}

    ChallengeProgressScreen(
        state = state,
        onFinish = { viewModel.finish(onFinished) },
        onGiveUp = { viewModel.giveUp(onGaveUp) },
        onBackToReady = onBackToReady,
        modifier = modifier
    )

    if (giveUpFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGiveUpFailure,
            text = { Text(text = GIVE_UP_FAILED_MESSAGE) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissGiveUpFailure) {
                    Text(text = CONFIRM_LABEL)
                }
            }
        )
    }
}

@Composable
fun ChallengeProgressScreen(
    state: ChallengeProgressUiState,
    onFinish: () -> Unit,
    onGiveUp: () -> Unit,
    onBackToReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmGiveUp by rememberSaveable { mutableStateOf(false) }

    DriverSideOverlay(
        background = {
            RouteMap(
                points = state.path.map { RoutePoint(it.latitude, it.longitude) },
                options = RouteMapOptions(
                    showEndpointMarkers = true,
                    inProgress = true,
                    contentPadding = driverSideOverlayContentPadding(),
                    currentPosition = state.path.lastOrNull()?.let {
                        CurrentPosition(
                            RoutePoint(it.latitude, it.longitude),
                            state.lap.headingDegrees
                        )
                    }
                ),
                modifier = Modifier.fillMaxSize()
            )
        },
        controls = {
            OverlayCard(modifier = Modifier.fillMaxWidth()) {
                if (!state.driving) {
                    Text(
                        text = CIRCLE_PROGRESS_TITLE,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                when (state.phase) {
                    ChallengeProgressPhase.PREPARING -> StatusText(PROGRESS_PREPARING)
                    ChallengeProgressPhase.FAILED -> StatusText(PROGRESS_FAILED)
                    ChallengeProgressPhase.RECORDING, ChallengeProgressPhase.FINISHED -> {
                        if (state.showsFinishGuidance) {
                            Guidance(FINISH_GUIDANCE, MaterialTheme.colorScheme.primary)
                        }
                        ProgressMeasurements(state)
                    }
                }
                if (state.phase != ChallengeProgressPhase.FAILED && !state.showsFinishGuidance) {
                    state.controlBlockedReason?.let {
                        Guidance(it, MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state.phase == ChallengeProgressPhase.FAILED) {
                CarButton(
                    text = BACK_TO_READY_LABEL,
                    onClick = onBackToReady,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!state.driving) {
                // 주행 중에는 쓸 수 없는 버튼을 숨겨 지도를 가리지 않는다. 정차하면 다시 보인다.
                CarButton(
                    text = FINISH_LABEL,
                    onClick = onFinish,
                    enabled = state.canControl,
                    modifier = Modifier.fillMaxWidth()
                )
                CarButton(
                    text = GIVE_UP_LABEL,
                    onClick = { confirmGiveUp = true },
                    enabled = state.canControl,
                    style = CarButtonStyle.Neutral,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        modifier = modifier
    )

    if (confirmGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text(text = GIVE_UP_CONFIRM_TITLE) },
            text = { Text(text = GIVE_UP_CONFIRM_MESSAGE) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmGiveUp = false
                        onGiveUp()
                    },
                    enabled = state.canControl
                ) {
                    Text(text = GIVE_UP_CONFIRM_LABEL)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmGiveUp = false }) {
                    Text(text = GIVE_UP_CANCEL_LABEL)
                }
            }
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge)
}

/** 주행 중에는 한눈에 읽도록 측정값을 크게 보여주고, 정차 중에는 회전 진행률까지 보여준다. */
@Composable
private fun ProgressMeasurements(state: ChallengeProgressUiState) {
    val large = state.driving
    Measurement(ELAPSED_LABEL, formatElapsed(state.elapsedMillis), large)
    Measurement(DISTANCE_LABEL, formatDistance(state.distanceMeters), large)
    Measurement(
        DISTANCE_TO_START_LABEL,
        formatDistanceToStart(state.lap.distanceToStartMeters),
        large
    )
    if (!large) Measurement(TURN_PROGRESS_LABEL, formatTurnPercent(state.lap.turnPercent))
}

/** 짧고 크게 쓰는 안내. 주행 중에도 읽을 수 있도록 [color]로 강조한다. */
@Composable
private fun Guidance(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, color = color)
}

@Composable
internal fun Measurement(label: String, value: String, large: Boolean = false) {
    val typography = MaterialTheme.typography
    // 이름과 값을 한 번에 읽도록 묶는다.
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = label,
            style = if (large) typography.titleLarge else typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = if (large) typography.displayMedium else typography.displaySmall)
    }
}
