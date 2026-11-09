package kr.roadcanvas.app.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.routeThumbnail

/** 공개 작품 목록 화면의 목록, 항목별 표 수·썸네일과 지도에 그릴 선택한 작품 */
data class PublicGalleryUiState(
    val list: GalleryListState = GalleryListState.Loading,
    /** 화면에 보인 적 있는 항목만 들어 있다. */
    val extras: Map<String, ArtworkItemExtras> = emptyMap(),
    val selected: SelectedArtwork? = null
)

/** 목록에서 고른 작품. 경로를 읽는 중이면 [route]와 [failure]가 모두 null이다. */
data class SelectedArtwork(
    val id: String,
    val route: List<RoutePoint>? = null,
    val failure: GalleryFailure? = null
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
 * 첫 페이지를 불러오면 맨 위 작품을 골라 지도에 경로를 그린다. 경로는 썸네일과 지도가 같은 요청을 함께 쓴다.
 */
class PublicGalleryViewModel(private val gallery: PublicGallery) : ViewModel() {
    private val mutableState = MutableStateFlow(PublicGalleryUiState())
    val state: StateFlow<PublicGalleryUiState> = mutableState.asStateFlow()

    private var cursor: PublicArtworkCursor? = null
    private var listJob: Job? = null
    private val requested = mutableSetOf<String>()
    private val routes = mutableMapOf<String, Deferred<GalleryResult<List<RoutePoint>>>>()
    private var selectJob: Job? = null

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
        routes.clear()
        selectJob?.cancel()
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
            (list as? GalleryListState.Loaded)?.items?.firstOrNull()?.let(::select)
        }
    }

    /** [artwork]를 골라 경로를 지도에 그린다. 이미 고른 작품이면 그대로 둔다. */
    fun select(artwork: PublicArtwork) {
        if (mutableState.value.selected?.id == artwork.id) return
        selectJob?.cancel()
        mutableState.update { it.copy(selected = SelectedArtwork(artwork.id)) }
        selectJob = viewModelScope.launch {
            val selected = when (val route = loadRoute(artwork)) {
                is GalleryResult.Success -> SelectedArtwork(artwork.id, route = route.value)
                is GalleryResult.Failure -> SelectedArtwork(artwork.id, failure = route.reason)
            }
            mutableState.update {
                if (it.selected?.id ==
                    artwork.id
                ) {
                    it.copy(selected = selected)
                } else {
                    it
                }
            }
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
            val route = loadRoute(artwork)
            if (route is GalleryResult.Success) {
                updateExtras(artwork.id) { it.copy(thumbnail = routeThumbnail(route.value)) }
            }
        }
    }

    /** 작품 경로. 같은 작품은 요청 하나를 함께 기다리고, 실패하면 다음에 다시 요청한다. */
    private suspend fun loadRoute(artwork: PublicArtwork): GalleryResult<List<RoutePoint>> {
        val request = routes.getOrPut(artwork.id) {
            viewModelScope.async { gallery.route(artwork) }
        }
        val result = request.await()
        if (result is GalleryResult.Failure && routes[artwork.id] === request) {
            routes.remove(artwork.id)
        }
        return result
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
