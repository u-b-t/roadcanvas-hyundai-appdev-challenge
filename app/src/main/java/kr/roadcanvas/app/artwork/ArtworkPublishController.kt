package kr.roadcanvas.app.artwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult

/**
 * 화면에 보여줄 작품 공개 상태.
 * [failure]는 이번 실행에서 등록에 실패한 이유이며, 앱을 다시 열어 저장된 실패만 남았으면 null이다.
 */
data class ArtworkPublishUiState(
    val status: PublishStatus,
    /** 이름을 붙여 공개할 수 있는 기록인지 */
    val canPublish: Boolean,
    val failure: PublishFailure? = null,
    val consentVisible: Boolean = false,
    val unpublishing: Boolean = false,
    val unpublishFailure: PublishFailure? = null,
    val titleSyncFailure: PublishFailure? = null
)

/**
 * 결과 화면과 내 그림 상세가 함께 쓰는 공개 상태와 조작. 자유그림을 [show]하면 저장된 공개 상태를 불러온다.
 * 공개는 동의 안내를 연 뒤 [confirmPublish]로만 시작한다. 다른 화면의 요청이 끝나면 상태를 다시 불러온다.
 */
class ArtworkPublishController(
    private val publisher: ArtworkPublisher,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<ArtworkPublishUiState?>(null)

    /** 공개 대상이 아닌 기록이거나 상태를 불러오기 전이면 null이다. */
    val state: StateFlow<ArtworkPublishUiState?> = mutableState.asStateFlow()

    private var drawing: CompletedDrawing? = null
    private var observeJob: Job? = null

    /** 저장한 완료 기록 [drawing]의 공개 상태를 보여준다. 자유그림이 아니거나 null이면 숨긴다. */
    fun show(drawing: CompletedDrawing?) {
        observeJob?.cancel()
        this.drawing = drawing
        mutableState.value = null
        if (drawing == null || drawing.mode != DrawingMode.FREE_DRAWING) return
        observeJob = scope.launch {
            publisher.inFlight.map { drawing.id in it }.distinctUntilChanged().collect {
                refresh(drawing.id)
            }
        }
    }

    /** 이름을 바꿨다. 공개할 수 있는지 다시 보고, 공개 중이면 작품 이름에도 반영한다. */
    fun onRenamed(name: String?) {
        val current = drawing ?: return
        val renamed = current.copy(name = name)
        drawing = renamed
        mutableState.update { it?.copy(canPublish = publisher.publishable(renamed)) }
        if (mutableState.value?.status != PublishStatus.PUBLISHED) return
        scope.launch {
            val result = publisher.syncTitle(renamed.id, name)
            updateFor(renamed.id) {
                it.copy(titleSyncFailure = (result as? ArtworkResult.Failure)?.reason)
            }
        }
    }

    /** 공개하지 않은 작품이면 공개 동의 안내를 연다. */
    fun requestPublish() {
        mutableState.update {
            if (it != null && it.canPublish && it.status == PublishStatus.UNPUBLISHED) {
                it.copy(consentVisible = true)
            } else {
                it
            }
        }
    }

    fun cancelConsent() {
        mutableState.update { it?.copy(consentVisible = false) }
    }

    /** 동의 안내에서 동의했다. */
    fun confirmPublish() {
        if (mutableState.value?.consentVisible != true) return
        startPublish()
    }

    /** 등록에 실패한 작품을 같은 작품 ID로 다시 등록한다. 이미 동의했으므로 안내를 다시 열지 않는다. */
    fun retry() {
        if (mutableState.value?.status != PublishStatus.FAILED) return
        startPublish()
    }

    /** 공개한 작품을 비공개로 되돌린다. */
    fun unpublish() {
        val current = drawing ?: return
        val state = mutableState.value ?: return
        if (state.status != PublishStatus.PUBLISHED || state.unpublishing) return
        mutableState.value = state.copy(unpublishing = true, unpublishFailure = null)
        scope.launch {
            val result = publisher.unpublish(current.id)
            updateFor(current.id) {
                when (result) {
                    ArtworkResult.Success -> it.copy(
                        status = PublishStatus.UNPUBLISHED,
                        unpublishing = false,
                        titleSyncFailure = null
                    )
                    is ArtworkResult.Failure ->
                        it.copy(unpublishing = false, unpublishFailure = result.reason)
                }
            }
        }
    }

    private fun startPublish() {
        val current = drawing ?: return
        val state = mutableState.value ?: return
        if (!state.canPublish) return
        mutableState.value = state.copy(
            status = PublishStatus.PUBLISHING,
            consentVisible = false,
            failure = null
        )
        scope.launch {
            val result = publisher.publish(current)
            updateFor(current.id) {
                when (result) {
                    ArtworkResult.Success -> it.copy(status = PublishStatus.PUBLISHED)
                    is ArtworkResult.Failure ->
                        it.copy(status = PublishStatus.FAILED, failure = result.reason)
                }
            }
        }
    }

    /** 저장된 상태를 다시 읽는다. 상태가 실패로 같으면 이번 실행에서 받은 실패 이유를 유지한다. */
    private suspend fun refresh(drawingId: Long) {
        val loaded = publisher.status(drawingId)
        val status = (loaded as? StorageResult.Success)?.value ?: PublishStatus.UNPUBLISHED
        val current = drawing?.takeIf { it.id == drawingId } ?: return
        mutableState.update { previous ->
            val keep = previous?.takeIf { it.status == status }
            ArtworkPublishUiState(
                status = status,
                canPublish = publisher.publishable(current),
                failure = keep?.failure ?: if (loaded is StorageResult.Failure) {
                    PublishFailure.STORAGE
                } else {
                    null
                },
                unpublishFailure = keep?.unpublishFailure,
                titleSyncFailure = keep?.titleSyncFailure
            )
        }
    }

    /** 보여주는 기록이 그사이 바뀌지 않았을 때만 상태를 바꾼다. */
    private fun updateFor(
        drawingId: Long,
        transform: (ArtworkPublishUiState) -> ArtworkPublishUiState
    ) {
        if (drawing?.id != drawingId) return
        mutableState.update { it?.let(transform) }
    }
}
