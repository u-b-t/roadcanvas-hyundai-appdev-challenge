package kr.roadcanvas.app.mydrawings

import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary

/** 내 그림 화면의 목록, 선택한 기록의 상세와 삭제·이름 변경 진행 상태 */
data class MyDrawingsUiState(
    val list: DrawingListState = DrawingListState.Loading,
    val selectedId: Long? = null,
    /** 선택한 기록이 없으면 null이다. */
    val detail: DrawingDetailState? = null,
    val deleteConfirmVisible: Boolean = false,
    val deleting: Boolean = false,
    val deleteFailed: Boolean = false,
    /** 선택한 기록의 이름을 바꾸는 중이면 null이 아니다. */
    val rename: RenameState? = null
)

/** 상세의 이름 변경 입력 상태 */
data class RenameState(val saving: Boolean = false, val failed: Boolean = false)

sealed interface DrawingListState {
    data object Loading : DrawingListState

    data object Empty : DrawingListState

    data object Error : DrawingListState

    /** [items]는 최신순이다. [best]는 점수를 매기는 모드의 최고 점수 기록이며 같은 점수면 최근 기록이다. */
    data class Loaded(val items: List<CompletedDrawingSummary>) : DrawingListState {
        val best: CompletedDrawingSummary? = bestOf(items)
    }
}

sealed interface DrawingDetailState {
    data object Loading : DrawingDetailState

    data class Loaded(val drawing: CompletedDrawing) : DrawingDetailState

    /** 목록을 불러온 뒤 다른 곳에서 삭제되어 기록을 찾을 수 없다. */
    data object NotFound : DrawingDetailState

    data object Error : DrawingDetailState
}

/**
 * 최신순 [items]에서 최고 점수 기록. 같은 점수면 앞에 있는 최근 기록을 고른다.
 * 점수가 없는 자유그림은 고르지 않는다.
 */
fun bestOf(items: List<CompletedDrawingSummary>): CompletedDrawingSummary? =
    items.filter { it.mode.scored }.maxByOrNull { it.score }

/**
 * [deletedId]를 지운 뒤 선택할 기록. 바로 다음 기록을 고르고, 마지막 기록이었다면 바로 앞 기록을 고른다.
 * 남는 기록이 없으면 null이다.
 */
fun nextSelectionAfterDelete(items: List<CompletedDrawingSummary>, deletedId: Long): Long? {
    val index = items.indexOfFirst { it.id == deletedId }
    val remaining = items.filterNot { it.id == deletedId }
    if (remaining.isEmpty()) return null
    if (index < 0) return remaining.first().id
    return remaining[index.coerceAtMost(remaining.lastIndex)].id
}
