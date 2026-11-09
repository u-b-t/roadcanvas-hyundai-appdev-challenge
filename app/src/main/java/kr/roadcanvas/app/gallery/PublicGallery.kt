package kr.roadcanvas.app.gallery

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.roadcanvas.app.drawing.RoutePoint

/** 한 번에 읽는 공개 작품 수. 서버 조회 계약의 기본 페이지 크기다. */
const val PUBLIC_ARTWORK_PAGE_SIZE = 20

/** 공개 작품 조회·투표 결과. 실패하면 예외 대신 [Failure]로 전달한다. */
sealed interface GalleryResult<out T> {
    data class Success<out T>(val value: T) : GalleryResult<T>

    data class Failure(val reason: GalleryFailure) : GalleryResult<Nothing>
}

/** 상세 화면에 보이는 작품, 경로, 표 수와 본인 투표 여부. 확인하지 못한 값은 null이다. */
data class PublicArtworkDetail(
    val artwork: PublicArtwork,
    val route: List<RoutePoint>,
    val votes: Long?,
    val voted: Boolean?
)

/** 투표하거나 취소한 뒤의 본인 투표 여부와 표 수. 표 수를 다시 읽지 못하면 null이다. */
data class VoteStatus(val voted: Boolean, val votes: Long?)

/**
 * 공개 작품 목록·상세 조회와 투표를 서버 저장소 [store] 위에서 처리한다.
 * [currentUid]는 익명 인증된 사용자 UID이며 인증 전이면 null이다.
 */
class PublicGallery(
    private val store: PublicArtworkStore,
    private val currentUid: () -> String?,
    private val onFailure: (Throwable) -> Unit = {}
) {
    private val mutableVoteChanges = MutableStateFlow(emptyMap<String, Long>())

    /** 이 앱에서 투표하거나 취소한 뒤 다시 읽은 작품별 표 수. 목록이 다시 조회하지 않고 반영한다. */
    val voteChanges: StateFlow<Map<String, Long>> = mutableVoteChanges.asStateFlow()

    /** 공개 작품 한 페이지. 처음이면 [after]가 null이다. */
    suspend fun page(
        after: PublicArtworkCursor?,
        limit: Int = PUBLIC_ARTWORK_PAGE_SIZE
    ): GalleryResult<PublicArtworkPage> = attempt { store.publishedPage(after, limit) }

    /** 작품의 표 수. 목록에서는 화면에 보이는 항목만 요청한다. */
    suspend fun voteCount(artworkId: String): GalleryResult<Long> =
        attempt { store.voteCount(artworkId) }

    /** 작품의 경로를 청크 index 순서로 읽고 검증한다. */
    suspend fun route(artwork: PublicArtwork): GalleryResult<List<RoutePoint>> =
        attempt { loadRoute(artwork) }

    /**
     * 공개 중인 작품과 경로를 읽는다. 비공개로 바뀌었으면 [GalleryFailure.NOT_PUBLISHED]다.
     * 표 수와 본인 투표 여부는 확인하지 못해도 상세를 보여주도록 null로 둔다.
     */
    suspend fun detail(artworkId: String): GalleryResult<PublicArtworkDetail> = attempt {
        val artwork = store.artwork(artworkId) ?: notPublished()
        val route = loadRoute(artwork)
        val votes = (voteCount(artworkId) as? GalleryResult.Success)?.value
        val voted = currentUid()?.let { uid ->
            (attempt { store.hasVoted(artworkId, uid) } as? GalleryResult.Success)?.value
        }
        PublicArtworkDetail(artwork, route, votes, voted)
    }

    /**
     * 투표한다. 서버가 같은 사용자의 투표 문서를 다시 만들지 못하게 하므로, 앞선 요청의 응답만 잃은 경우를 위해
     * 만들기 전에 본인 투표가 있는지 확인하고 이미 있으면 그대로 투표한 상태로 둔다.
     */
    suspend fun vote(artworkId: String): GalleryResult<VoteStatus> = attempt {
        val uid = currentUid() ?: notSignedIn()
        if (!store.hasVoted(artworkId, uid)) store.vote(artworkId, uid)
        VoteStatus(voted = true, votes = refreshedCount(artworkId))
    }

    /** 본인 투표를 취소한다. 투표 문서가 이미 없으면 그대로 취소한 상태로 둔다. */
    suspend fun unvote(artworkId: String): GalleryResult<VoteStatus> = attempt {
        val uid = currentUid() ?: notSignedIn()
        if (store.hasVoted(artworkId, uid)) store.unvote(artworkId, uid)
        VoteStatus(voted = false, votes = refreshedCount(artworkId))
    }

    private suspend fun loadRoute(artwork: PublicArtwork): List<RoutePoint> =
        decodeArtworkRoute(store.chunks(artwork.id), artwork.pointChunkCount)

    private suspend fun refreshedCount(artworkId: String): Long? =
        (voteCount(artworkId) as? GalleryResult.Success)?.value?.also { votes ->
            mutableVoteChanges.update { it + (artworkId to votes) }
        }

    private suspend fun <T> attempt(block: suspend () -> T): GalleryResult<T> = try {
        GalleryResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        GalleryResult.Failure(failureOf(e))
    }

    private fun failureOf(error: Throwable): GalleryFailure = when (error) {
        is GalleryStoreException -> error.failure
        is IOException -> GalleryFailure.NETWORK
        else -> GalleryFailure.UNKNOWN
    }

    private fun notPublished(): Nothing =
        throw GalleryStoreException(GalleryFailure.NOT_PUBLISHED, "공개 중인 작품이 아닙니다.")

    private fun notSignedIn(): Nothing =
        throw GalleryStoreException(GalleryFailure.AUTH, "익명 계정을 확인하지 못했습니다.")
}
