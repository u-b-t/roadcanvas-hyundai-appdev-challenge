package kr.roadcanvas.app.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ArtworkDetailState {
    data object Loading : ArtworkDetailState

    /** 작성자가 비공개로 바꿨거나 작품이 없어 볼 수 없다. */
    data object NotPublished : ArtworkDetailState

    data class Error(val reason: GalleryFailure) : ArtworkDetailState

    /** [voteFailure]는 마지막 투표·취소 요청이 실패한 이유다. */
    data class Loaded(
        val detail: PublicArtworkDetail,
        val voting: Boolean = false,
        val voteFailure: GalleryFailure? = null
    ) : ArtworkDetailState
}

/** 공개 작품 하나의 경로와 표 수를 보여주고 투표·취소를 처리한다. */
class PublicArtworkDetailViewModel(
    private val gallery: PublicGallery,
    private val artworkId: String
) : ViewModel() {
    private val mutableState = MutableStateFlow<ArtworkDetailState>(ArtworkDetailState.Loading)
    val state: StateFlow<ArtworkDetailState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** 작품을 다시 불러온다. */
    fun retry() {
        load()
    }

    /** 투표하지 않았으면 투표하고, 투표했으면 취소한다. 요청 중에는 무시한다. */
    fun toggleVote() {
        val loaded = mutableState.value as? ArtworkDetailState.Loaded ?: return
        if (loaded.voting) return
        val cancel = loaded.detail.voted == true
        mutableState.value = loaded.copy(voting = true, voteFailure = null)
        viewModelScope.launch {
            val result = if (cancel) gallery.unvote(artworkId) else gallery.vote(artworkId)
            mutableState.update { state ->
                val current = state as? ArtworkDetailState.Loaded ?: return@update state
                when (result) {
                    is GalleryResult.Success -> current.copy(
                        detail = current.detail.copy(
                            voted = result.value.voted,
                            votes = result.value.votes
                        ),
                        voting = false
                    )
                    is GalleryResult.Failure ->
                        if (result.reason == GalleryFailure.NOT_PUBLISHED) {
                            ArtworkDetailState.NotPublished
                        } else {
                            current.copy(voting = false, voteFailure = result.reason)
                        }
                }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        mutableState.value = ArtworkDetailState.Loading
        loadJob = viewModelScope.launch {
            mutableState.value = when (val result = gallery.detail(artworkId)) {
                is GalleryResult.Success -> ArtworkDetailState.Loaded(result.value)
                is GalleryResult.Failure -> when (result.reason) {
                    GalleryFailure.NOT_PUBLISHED -> ArtworkDetailState.NotPublished
                    else -> ArtworkDetailState.Error(result.reason)
                }
            }
        }
    }
}
