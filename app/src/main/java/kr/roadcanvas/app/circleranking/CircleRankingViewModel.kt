package kr.roadcanvas.app.circleranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 순위 화면 상태 */
sealed interface CircleRankingUiState {
    data object Loading : CircleRankingUiState

    data class Loaded(val board: CircleRankingBoard) : CircleRankingUiState

    data class Failed(val reason: CircleRankingFailure) : CircleRankingUiState
}

/** 들어오면 순위를 읽는다. [refresh]는 다시 읽는다. 실시간으로 구독하지 않는다. */
class CircleRankingViewModel(private val ranking: CircleRanking) : ViewModel() {
    private val mutableState = MutableStateFlow<CircleRankingUiState>(CircleRankingUiState.Loading)
    val state: StateFlow<CircleRankingUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        job?.cancel()
        mutableState.value = CircleRankingUiState.Loading
        job = viewModelScope.launch {
            mutableState.value = when (val result = ranking.load()) {
                is CircleRankingResult.Success -> CircleRankingUiState.Loaded(result.board)
                is CircleRankingResult.Failure -> CircleRankingUiState.Failed(result.reason)
            }
        }
    }
}
