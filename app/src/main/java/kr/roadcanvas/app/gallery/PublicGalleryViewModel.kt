package kr.roadcanvas.app.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.routeThumbnail

/** 공개 작품 목록 화면의 목록과 항목별 표 수·썸네일 */
data class PublicGalleryUiState(
    val list: GalleryListState = GalleryListState.Loading,
    /** 화면에 보인 적 있는 항목만 들어 있다. */
    val extras: Map<String, ArtworkItemExtras> = emptyMap()
)

sealed interface GalleryListState {
    data object Loading : GalleryListState

    data object Empty : GalleryListState

    data class Error(val reason: GalleryFailure) : GalleryListState

    /** [items]는 최신순이다. 다음 페이지가 있으면 [hasMore]다. */
    data class Loaded(
        val items: List<PublicArtwork>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreFailure: GalleryFailure? = null
    ) : GalleryListState
}

/** 목록 항목의 표 수와 썸네일. 아직 모르거나 읽지 못하면 null이다. */
data class ArtworkItemExtras(
    val votes: Long? = null,
    val votesFailed: Boolean = false,
    val thumbnail: List<RoutePoint>? = null
)

/**
 * 공개 작품을 최신순으로 한 페이지씩 불러온다.
 * 표 수와 경로 썸네일은 서버 읽기 비용을 줄이려고 화면에 보인 항목만 한 번씩 요청한다.
 */
class PublicGalleryViewModel(private val gallery: PublicGallery) : ViewModel() {
    private val mutableState = MutableStateFlow(PublicGalleryUiState())
    val state: StateFlow<PublicGalleryUiState> = mutableState.asStateFlow()

    private var cursor: PublicArtworkCursor? = null
    private var listJob: Job? = null
    private val requested = mutableSetOf<String>()

    init {
        refresh()
        viewModelScope.launch {
            // 상세 화면에서 투표하거나 취소한 결과를 다시 조회하지 않고 반영한다.
            gallery.voteChanges.collect { changes ->
                mutableState.update { state ->
                    state.copy(
                        extras = state.extras.mapValues { (id, extras) ->
                            changes[id]?.let { extras.copy(votes = it, votesFailed = false) }
                                ?: extras
                        }
                    )
                }
            }
        }
    }

    /** 목록을 처음부터 다시 불러온다. 항목별 표 수도 다시 요청한다. */
    fun refresh() {
        listJob?.cancel()
        cursor = null
        requested.clear()
        mutableState.value = PublicGalleryUiState()
        listJob = viewModelScope.launch {
            val list = when (val result = gallery.page(after = null)) {
                is GalleryResult.Failure -> GalleryListState.Error(result.reason)
                is GalleryResult.Success -> {
                    cursor = result.value.next
                    if (result.value.items.isEmpty()) {
                        GalleryListState.Empty
                    } else {
                        GalleryListState.Loaded(result.value.items, result.value.next != null)
                    }
                }
            }
            mutableState.update { it.copy(list = list) }
        }
    }

    /** 다음 페이지를 이어 붙인다. 이미 앞 페이지와 겹치는 작품은 빼고 붙인다. */
    fun loadMore() {
        val list = mutableState.value.list as? GalleryListState.Loaded ?: return
        val after = cursor
        if (list.loadingMore || !list.hasMore || after == null) return
        setLoaded { it.copy(loadingMore = true, loadMoreFailure = null) }
        listJob = viewModelScope.launch {
            when (val result = gallery.page(after)) {
                is GalleryResult.Failure -> setLoaded {
                    it.copy(loadingMore = false, loadMoreFailure = result.reason)
                }
                is GalleryResult.Success -> {
                    cursor = result.value.next
                    setLoaded { loaded ->
                        val known = loaded.items.map { it.id }.toSet()
                        loaded.copy(
                            items = loaded.items + result.value.items.filterNot { it.id in known },
                            hasMore = result.value.next != null,
                            loadingMore = false
                        )
                    }
                }
            }
        }
    }

    /** [artwork] 항목이 화면에 보였다. 처음이면 표 수와 경로 썸네일을 요청한다. */
    fun onItemVisible(artwork: PublicArtwork) {
        if (!requested.add(artwork.id)) return
        updateExtras(artwork.id) { it }
        viewModelScope.launch {
            val votes = gallery.voteCount(artwork.id)
            updateExtras(artwork.id) {
                when (votes) {
                    is GalleryResult.Success -> it.copy(votes = votes.value, votesFailed = false)
                    is GalleryResult.Failure -> it.copy(votesFailed = true)
                }
            }
        }
        viewModelScope.launch {
            val route = gallery.route(artwork)
            if (route is GalleryResult.Success) {
                updateExtras(artwork.id) { it.copy(thumbnail = routeThumbnail(route.value)) }
            }
        }
    }

    private fun setLoaded(transform: (GalleryListState.Loaded) -> GalleryListState.Loaded) {
        mutableState.update { state ->
            (state.list as? GalleryListState.Loaded)?.let { state.copy(list = transform(it)) }
                ?: state
        }
    }

    private fun updateExtras(id: String, transform: (ArtworkItemExtras) -> ArtworkItemExtras) {
        mutableState.update { state ->
            if (id !in requested) return@update state
            state.copy(
                extras =
                state.extras + (id to transform(state.extras[id] ?: ArtworkItemExtras()))
            )
        }
    }
}
