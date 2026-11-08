package kr.roadcanvas.app.mydrawings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingNameValidation
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.validateDrawingName

/**
 * 완료 기록 요약 목록을 불러오고 선택한 기록만 경로까지 조회한다.
 * 처음에는 가장 최근 기록을 선택한다.
 */
class MyDrawingsViewModel(private val repository: CompletedDrawingRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MyDrawingsUiState())
    val state: StateFlow<MyDrawingsUiState> = mutableState.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadList()
    }

    /** 목록을 처음부터 다시 불러온다. */
    fun retry() {
        loadList()
    }

    /** [id] 기록을 선택하고 상세를 불러온다. 삭제 중에는 선택을 바꾸지 않는다. */
    fun select(id: Long) {
        if (mutableState.value.deleting) return
        detailJob?.cancel()
        mutableState.update {
            it.copy(
                selectedId = id,
                detail = DrawingDetailState.Loading,
                deleteFailed = false,
                rename = null
            )
        }
        detailJob = viewModelScope.launch {
            val detail = when (val result = repository.find(id)) {
                is StorageResult.Success ->
                    result.value?.let(DrawingDetailState::Loaded) ?: DrawingDetailState.NotFound
                is StorageResult.Failure -> DrawingDetailState.Error
            }
            mutableState.update { if (it.selectedId == id) it.copy(detail = detail) else it }
        }
    }

    /** 선택한 기록의 상세를 다시 불러온다. */
    fun retryDetail() {
        mutableState.value.selectedId?.let(::select)
    }

    /** 상세를 불러온 기록의 이름 변경 입력을 연다. */
    fun startRename() {
        mutableState.update {
            if (it.detail is DrawingDetailState.Loaded && !it.deleting) {
                it.copy(rename = RenameState())
            } else {
                it
            }
        }
    }

    /** 저장 중이 아니면 이름 변경 입력을 닫는다. */
    fun cancelRename() {
        mutableState.update { if (it.rename?.saving == true) it else it.copy(rename = null) }
    }

    /**
     * 선택한 기록의 이름을 [input]으로 바꾼다. 이름 규칙에 맞지 않으면 저장하지 않는다.
     * 성공하면 상세와 목록의 이름을 바꾸고 입력을 닫으며, 실패하면 입력을 남기고 실패를 표시한다.
     */
    fun saveName(input: String) {
        val current = mutableState.value
        val drawing = (current.detail as? DrawingDetailState.Loaded)?.drawing ?: return
        if (current.rename == null || current.rename.saving) return
        val validation = validateDrawingName(input, drawing.mode.nameRequired)
        val name = (validation as? DrawingNameValidation.Valid ?: return).name
        mutableState.update { it.copy(rename = RenameState(saving = true)) }
        viewModelScope.launch {
            val renamed = repository.rename(drawing.id, name)
            mutableState.update { state ->
                when (renamed) {
                    is StorageResult.Failure ->
                        state.copy(rename = state.rename?.let { RenameState(failed = true) })
                    is StorageResult.Success -> state.copy(
                        rename = null,
                        detail = (state.detail as? DrawingDetailState.Loaded)
                            ?.takeIf { it.drawing.id == drawing.id }
                            ?.let { DrawingDetailState.Loaded(it.drawing.copy(name = name)) }
                            ?: state.detail,
                        list = (state.list as? DrawingListState.Loaded)?.let { list ->
                            DrawingListState.Loaded(
                                list.items.map {
                                    if (it.id == drawing.id) it.copy(name = name) else it
                                }
                            )
                        } ?: state.list
                    )
                }
            }
        }
    }

    fun requestDelete() {
        mutableState.update {
            if (it.selectedId != null && !it.deleting && it.rename == null) {
                it.copy(deleteConfirmVisible = true)
            } else {
                it
            }
        }
    }

    fun cancelDelete() {
        mutableState.update { it.copy(deleteConfirmVisible = false) }
    }

    /** 선택한 기록을 삭제한다. 성공하면 다음 기록을 선택하고 실패하면 상세를 그대로 둔다. */
    fun confirmDelete() {
        val current = mutableState.value
        val id = current.selectedId
        if (id == null || current.deleting || current.list !is DrawingListState.Loaded) {
            mutableState.update { it.copy(deleteConfirmVisible = false) }
            return
        }
        mutableState.update {
            it.copy(deleteConfirmVisible = false, deleting = true, deleteFailed = false)
        }
        viewModelScope.launch {
            when (repository.delete(id)) {
                is StorageResult.Failure -> mutableState.update {
                    it.copy(deleting = false, deleteFailed = true)
                }
                is StorageResult.Success -> {
                    val items = (mutableState.value.list as? DrawingListState.Loaded)?.items
                        ?: current.list.items
                    mutableState.update { it.copy(deleting = false) }
                    showList(
                        items = items.filterNot { it.id == id },
                        selectId = nextSelectionAfterDelete(items, id)
                    )
                }
            }
        }
    }

    private fun loadList() {
        listJob?.cancel()
        detailJob?.cancel()
        mutableState.value = MyDrawingsUiState(list = DrawingListState.Loading)
        listJob = viewModelScope.launch {
            when (val result = repository.summaries()) {
                is StorageResult.Success -> showList(result.value, selectId = null)
                is StorageResult.Failure -> mutableState.update {
                    it.copy(list = DrawingListState.Error)
                }
            }
        }
    }

    private fun showList(items: List<CompletedDrawingSummary>, selectId: Long?) {
        if (items.isEmpty()) {
            detailJob?.cancel()
            mutableState.update {
                it.copy(list = DrawingListState.Empty, selectedId = null, detail = null)
            }
            return
        }
        mutableState.update { it.copy(list = DrawingListState.Loaded(items)) }
        select(selectId ?: items.first().id)
    }
}
