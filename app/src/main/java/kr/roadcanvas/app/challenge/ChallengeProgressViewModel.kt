package kr.roadcanvas.app.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingRequest
import kr.roadcanvas.app.recording.RecordingRunner
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.recording.distanceTo
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.territory.NEARBY_TERRITORY_RELOAD_METERS
import kr.roadcanvas.app.territory.TerritoryCall
import kr.roadcanvas.app.territory.TerritoryCell
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritoryMapCells
import kr.roadcanvas.app.territory.boundsAround
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider

/** 진행 화면 갱신 간격. 화면은 최대 1초에 한 번 갱신한다. */
const val PROGRESS_REFRESH_MILLIS = 1_000L

/**
 * 진행 화면의 기록 상태를 구독하고 종료·포기를 처리한다.
 * 기록 자체는 [RecordingRunner]가 화면과 상관없이 실행하므로, 화면이 멈추면 화면 갱신만 멈춘다.
 * 땅따먹기 예상 영역은 [estimateMillis]마다 경로가 늘었을 때만 [estimateDispatcher]에서 다시 계산한다.
 * 땅따먹기는 [territoryGame]으로 현재 위치 주변의 서버 칸을 읽어 다른 사용자 영토를 함께 보여준다.
 */
class ChallengeProgressViewModel(
    private val recorder: ChallengeRecorder,
    private val runner: RecordingRunner,
    private val drivingRestrictionProvider: DrivingRestrictionProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    refreshMillis: Long = PROGRESS_REFRESH_MILLIS,
    estimateMillis: Long = TERRITORY_ESTIMATE_REFRESH_MILLIS,
    estimateDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val territoryGame: TerritoryGame? = null
) : ViewModel() {
    init {
        // 프로세스가 다시 만들어져 진행 화면이 바로 복원되면 저장된 기록을 이어간다.
        // 시작 직후라 아직 Idle이어도 실행 중인 기록이 있으면 실행기가 이 요청을 무시한다.
        if (recorder.state.value == RecordingState.Idle) runner.run(RecordingRequest.Resume)
    }

    private val now = flow {
        while (true) {
            emit(clock())
            delay(refreshMillis)
        }
    }

    private val territoryPath = flow {
        while (true) {
            val recording = recorder.state.value as? RecordingState.Recording
            emit(recording?.path?.takeIf { recording.mode == DrawingMode.TERRITORY })
            delay(estimateMillis)
        }
    }

    // 경로가 길수록 계산이 오래 걸리므로 화면 갱신과 따로, 경로가 늘었을 때만 계산한다.
    private val territoryEstimate = territoryPath
        .distinctUntilChangedBy { it?.size }
        .map { path ->
            path?.let { withContext(estimateDispatcher) { territoryEstimateOf(it) } }
        }

    /**
     * 땅따먹기 중 현재 위치 주변의 서버 칸. 준비 화면처럼 처음 위치에서 읽고 [NEARBY_TERRITORY_RELOAD_METERS]보다
     * 멀리 움직이면 다시 읽는다. 읽지 못하거나 칸이 너무 많으면 마지막으로 읽은 칸을 그대로 둔다.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nearbyCells: StateFlow<List<TerritoryCell>> = (
        territoryGame?.let { game ->
            territoryPath
                .map { path -> path?.lastOrNull() }
                .filterNotNull()
                .distinctUntilChanged { old, new ->
                    old.distanceTo(new) <= NEARBY_TERRITORY_RELOAD_METERS
                }
                .mapLatest { point ->
                    val found = game.mapCells(
                        boundsAround(GeoPoint(point.latitude, point.longitude))
                    )
                    ((found as? TerritoryCall.Success)?.value as? TerritoryMapCells.Loaded)?.cells
                }
                .filterNotNull()
        } ?: flow { }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // 경로와 경과 시간은 갱신 시점에만 읽어 화면 갱신을 1초에 한 번으로 제한한다.
    val uiState: StateFlow<ChallengeProgressUiState> = combine(
        now,
        drivingRestrictionProvider.restriction,
        territoryEstimate
    ) { nowMillis, restriction, estimate ->
        challengeProgressOf(recorder.state.value, restriction, nowMillis, estimate)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = challengeProgressOf(
            recorder.state.value,
            drivingRestrictionProvider.restriction.value,
            clock()
        )
    )

    private val mutableGiveUpFailed = MutableStateFlow(false)

    /** 포기했지만 진행 중 기록을 지우지 못했다. */
    val giveUpFailed: StateFlow<Boolean> = mutableGiveUpFailed.asStateFlow()

    /** 위치 구독을 멈추고 종료한 기록을 [onFinished]로 넘긴다. */
    fun finish(onFinished: (RecordingState.Finished) -> Unit) {
        if (!drivingRestrictionProvider.restriction.value.allowsInteraction) return
        viewModelScope.launch {
            val finished = recorder.finish() ?: return@launch
            runner.stop()
            onFinished(finished)
        }
    }

    /** 진행 중 기록을 지우고 기록을 멈춘다. 지우지 못하면 기록을 계속한다. */
    fun giveUp(onGaveUp: () -> Unit) {
        if (!drivingRestrictionProvider.restriction.value.allowsInteraction) return
        viewModelScope.launch {
            when (recorder.giveUp()) {
                is StorageResult.Success -> {
                    mutableGiveUpFailed.value = false
                    runner.stop()
                    onGaveUp()
                }
                is StorageResult.Failure -> mutableGiveUpFailed.value = true
            }
        }
    }

    fun dismissGiveUpFailure() {
        mutableGiveUpFailed.value = false
    }
}
