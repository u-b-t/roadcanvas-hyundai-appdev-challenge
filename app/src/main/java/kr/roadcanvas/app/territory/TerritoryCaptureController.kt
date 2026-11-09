package kr.roadcanvas.app.territory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult

/** 화면에 보여줄 땅따먹기 완료 기록 하나의 서버 반영 상태 */
sealed interface TerritorySyncUiState {
    /** 기기에 보관한 획득을 읽는 중이다. */
    data object Loading : TerritorySyncUiState

    /** 이 기록으로 획득을 만든 적이 없다. 제출하면 칸으로 바꿔 제출한다. */
    data object NotSubmitted : TerritorySyncUiState

    data class Capture(val state: TerritoryCaptureState) : TerritorySyncUiState

    /** 기기 기록을 읽거나 저장하지 못해 제출하지 못했다. */
    data class Failed(val reason: TerritoryFailure) : TerritorySyncUiState
}

/** 다시 제출할 수 있는 상태. 반영됐거나, 너무 크거나, 기한이 지났거나, 제출 중이면 제출하지 않는다. */
val TerritorySyncUiState.canSubmit: Boolean
    get() = when (this) {
        TerritorySyncUiState.NotSubmitted, is TerritorySyncUiState.Failed -> true
        is TerritorySyncUiState.Capture -> state is TerritoryCaptureState.Pending
        TerritorySyncUiState.Loading -> false
    }

/**
 * 결과 화면과 내 그림 상세가 함께 쓰는 땅따먹기 기록의 서버 반영 상태와 제출.
 * 차지한 땅따먹기 기록을 [show]하면 기기에 보관한 획득 상태를 불러오고, [submit]으로 획득을 만들거나 다시 제출한다.
 * 다른 곳(앱 실행, 다른 화면)의 제출이 끝나면 상태를 다시 불러온다.
 */
class TerritoryCaptureController(
    private val game: TerritoryGame,
    private val scope: CoroutineScope
) {
    private class Target(val drawingId: Long, val claimed: TerritoryResult.Claimed)

    private val mutableState = MutableStateFlow<TerritorySyncUiState?>(null)

    /** 땅따먹기 기록이 아니거나 영역을 차지하지 못한 기록이면 null이다. */
    val state: StateFlow<TerritorySyncUiState?> = mutableState.asStateFlow()

    private var target: Target? = null
    private var observeJob: Job? = null
    private var submitJob: Job? = null

    /** 저장한 완료 기록 [drawing]의 서버 반영 상태를 보여준다. 차지한 땅따먹기 기록이 아니면 숨긴다. */
    fun show(drawing: CompletedDrawing?) {
        observeJob?.cancel()
        submitJob?.cancel()
        val claimed = drawing?.let(::claimedOf)
        val current = if (drawing != null && claimed != null) Target(drawing.id, claimed) else null
        target = current
        mutableState.value = if (current == null) null else TerritorySyncUiState.Loading
        if (current == null) return
        observeJob = scope.launch { game.inFlight.collect { refresh(current) } }
    }

    /**
     * 획득이 없으면 만들어 제출하고, 반영하지 못한 획득은 같은 획득 ID로 다시 제출한다.
     * 제출하면 그 작업을, 제출할 수 없는 상태면 null을 돌려준다. 이미 제출 중이면 그 작업이다.
     */
    fun submit(): Job? {
        val current = target ?: return null
        submitJob?.takeIf { it.isActive }?.let { return it }
        val state = mutableState.value
        if (state != null && state != TerritorySyncUiState.Loading && !state.canSubmit) return null
        val pendingCells =
            ((state as? TerritorySyncUiState.Capture)?.state as? TerritoryCaptureState.Pending)
                ?.cellCount ?: 0
        mutableState.value =
            TerritorySyncUiState.Capture(TerritoryCaptureState.Submitting(pendingCells))
        return scope.launch {
            val result = game.capture(current.drawingId, current.claimed)
            if (target !== current) return@launch
            mutableState.value = when (result) {
                is TerritoryCall.Success -> TerritorySyncUiState.Capture(result.value)
                is TerritoryCall.Failure -> TerritorySyncUiState.Failed(result.reason)
            }
        }.also { submitJob = it }
    }

    /**
     * 기기에 보관한 상태를 다시 읽는다. 이 화면이 제출하는 동안에는 그 결과를 기다린다.
     * 다시 읽어도 대기 중이면 이번 실행에서 받은 실패 이유를 유지한다.
     */
    private suspend fun refresh(current: Target) {
        if (submitJob?.isActive == true) return
        val loaded = game.state(current.drawingId)
        if (target !== current || submitJob?.isActive == true) return
        val next = when (loaded) {
            is TerritoryCall.Failure -> TerritorySyncUiState.Failed(loaded.reason)
            is TerritoryCall.Success -> loaded.value?.let(TerritorySyncUiState::Capture)
                ?: notCaptured(current.claimed)
        }
        mutableState.value = keepFailure(mutableState.value, next)
    }

    private fun keepFailure(
        previous: TerritorySyncUiState?,
        next: TerritorySyncUiState
    ): TerritorySyncUiState {
        val previousPending =
            (previous as? TerritorySyncUiState.Capture)?.state as? TerritoryCaptureState.Pending
        val nextPending =
            (next as? TerritorySyncUiState.Capture)?.state as? TerritoryCaptureState.Pending
        return if (previousPending?.failure != null &&
            nextPending?.failure == null &&
            nextPending != null
        ) {
            TerritorySyncUiState.Capture(nextPending.copy(failure = previousPending.failure))
        } else {
            next
        }
    }

    /** 획득을 만든 적이 없는 기록. 너무 큰 영역은 획득을 만들지 않으므로 칸 수로 다시 확인한다. */
    private fun notCaptured(claimed: TerritoryResult.Claimed): TerritorySyncUiState =
        when (val cells = territoryCellsOf(claimed.boundary)) {
            is TerritoryCellsResult.TooLarge ->
                TerritorySyncUiState.Capture(TerritoryCaptureState.TooLarge(cells.maxCells))
            is TerritoryCellsResult.Cells -> TerritorySyncUiState.NotSubmitted
        }

    /** 저장한 경로로 결과 화면과 같은 판정을 다시 한다. 차지하지 못했거나 땅따먹기가 아니면 null이다. */
    private fun claimedOf(drawing: CompletedDrawing): TerritoryResult.Claimed? {
        if (drawing.mode != DrawingMode.TERRITORY) return null
        return TerritoryCalculator().calculate(
            drawing.path.map { GeoPoint(it.latitude, it.longitude) }
        ) as? TerritoryResult.Claimed
    }
}
