package kr.roadcanvas.app.circleranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 순위 기록 상세 화면 상태 */
sealed interface CircleRecordUiState {
    data object Loading : CircleRecordUiState

    data class Loaded(val detail: CircleRecordDetail) : CircleRecordUiState

    data class Failed(val reason: CircleRankingFailure) : CircleRecordUiState
}

/** 들어오면 [uid] 사용자의 순위 기록을 읽는다. [retry]는 다시 읽는다. */
class CircleRecordDetailViewModel(private val ranking: CircleRanking, private val uid: String) :
    ViewModel() {
    private val mutableState = MutableStateFlow<CircleRecordUiState>(CircleRecordUiState.Loading)
    val state: StateFlow<CircleRecordUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        retry()
    }

    fun retry() {
        job?.cancel()
        mutableState.value = CircleRecordUiState.Loading
        job = viewModelScope.launch {
            mutableState.value = when (val result = ranking.detail(uid)) {
                is CircleRecordResult.Success -> CircleRecordUiState.Loaded(result.detail)
                is CircleRecordResult.Failure -> CircleRecordUiState.Failed(result.reason)
            }
        }
    }
}
