package kr.roadcanvas.app.gallery

import kr.roadcanvas.app.artwork.ArtworkChunk

/** 다른 사용자가 볼 수 있는 공개 작품. 거리는 미터, 시간은 밀리초다. */
data class PublicArtwork(
    val id: String,
    val title: String,
    val nickname: String,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointChunkCount: Int,
    val createdAtMillis: Long
)

/** 다음 페이지를 이어 읽는 위치. 내용은 서버 저장소 구현만 안다. */
class PublicArtworkCursor(val value: Any)

/** 공개 작품 한 페이지. 끝 페이지면 [next]가 null이다. */
data class PublicArtworkPage(val items: List<PublicArtwork>, val next: PublicArtworkCursor?)

/** 공개 작품을 조회하거나 투표하지 못한 이유. 화면이 안내 문구를 고르는 데 쓴다. */
enum class GalleryFailure {
    /** 서버에 연결하지 못했다. */
    NETWORK,

    /** 익명 계정을 확인하지 못했다. */
    AUTH,

    /** 작품이 비공개로 바뀌었거나 없어 볼 수 없다. */
    NOT_PUBLISHED,

    /** 저장된 경로 청크가 빠졌거나 좌표가 올바르지 않다. */
    INVALID_ROUTE,

    /** 서버가 요청을 거부했거나 알 수 없는 오류다. */
    UNKNOWN
}

/** 서버 저장소 오류를 조회 실패 이유로 알린다. */
class GalleryStoreException(
    val failure: GalleryFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 공개 작품과 투표 서버 저장소. 서버 계약은 docs/firebase-artwork-client.md를 따른다.
 * 실패하면 [GalleryStoreException]이나 다른 예외를 던진다.
 */
interface PublicArtworkStore {
    /** `visibility == PUBLISHED`인 작품을 최신순으로 [after] 다음부터 [limit]개까지 읽는다. */
    suspend fun publishedPage(after: PublicArtworkCursor?, limit: Int): PublicArtworkPage

    /** 공개 중인 [artworkId] 작품. 비공개로 바뀌었거나 없으면 null이다. */
    suspend fun artwork(artworkId: String): PublicArtwork?

    /** 저장된 경로 청크. index 순서로 돌려준다. */
    suspend fun chunks(artworkId: String): List<ArtworkChunk>

    /** `votes` 하위 컬렉션의 `count()` 집계 */
    suspend fun voteCount(artworkId: String): Long

    /** [uid] 사용자의 투표 문서가 있는지 */
    suspend fun hasVoted(artworkId: String, uid: String): Boolean

    /** [uid] 사용자의 투표 문서를 만든다. 이미 있으면 서버가 거부한다. */
    suspend fun vote(artworkId: String, uid: String)

    /** [uid] 사용자의 투표 문서를 지운다. */
    suspend fun unvote(artworkId: String, uid: String)
}

/** 서버가 연결되지 않은 빌드에서 쓰는 저장소. 모든 요청이 연결 실패다. */
object UnavailablePublicArtworkStore : PublicArtworkStore {
    private fun unavailable(): Nothing =
        throw GalleryStoreException(GalleryFailure.NETWORK, "작품 서버가 연결되지 않았습니다.")

    override suspend fun publishedPage(after: PublicArtworkCursor?, limit: Int) = unavailable()

    override suspend fun artwork(artworkId: String) = unavailable()

    override suspend fun chunks(artworkId: String) = unavailable()

    override suspend fun voteCount(artworkId: String) = unavailable()

    override suspend fun hasVoted(artworkId: String, uid: String) = unavailable()

    override suspend fun vote(artworkId: String, uid: String) = unavailable()

    override suspend fun unvote(artworkId: String, uid: String) = unavailable()
}
