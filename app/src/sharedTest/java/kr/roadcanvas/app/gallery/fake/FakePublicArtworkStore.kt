package kr.roadcanvas.app.gallery.fake

import kr.roadcanvas.app.gallery.GalleryFailure
import kr.roadcanvas.app.gallery.GalleryStoreException
import kr.roadcanvas.app.gallery.PublicArtwork
import kr.roadcanvas.app.gallery.PublicArtworkChunk
import kr.roadcanvas.app.gallery.PublicArtworkCursor
import kr.roadcanvas.app.gallery.PublicArtworkPage
import kr.roadcanvas.app.gallery.PublicArtworkStore

/** 서버에 저장된 작품 문서와 청크, 투표. [published]가 false면 작성자가 비공개(DRAFT)로 바꾼 작품이다. */
data class FakeServerArtwork(
    val artwork: PublicArtwork,
    val chunks: List<PublicArtworkChunk>,
    val published: Boolean = true,
    val voters: Set<String> = emptySet()
)

/**
 * 메모리에 공개 작품을 보관하는 서버 저장소. 서버 규칙처럼 비공개 작품은 목록·상세·청크·투표를 거부하고
 * 같은 사용자의 투표 문서를 다시 만들지 못한다. 목록은 생성 시각 최신순이다.
 * [networkFailure]를 지정하면 모든 요청이 연결 실패하고, [loseNextVoteResponse]면 다음 투표는 서버에 저장된 뒤
 * 응답만 실패한다.
 */
class FakePublicArtworkStore : PublicArtworkStore {
    private val artworks = linkedMapOf<String, FakeServerArtwork>()

    var networkFailure: Boolean = false
    var loseNextVoteResponse: Boolean = false

    /** 지금까지 받은 요청. `이름:작품 ID` 형식이다. */
    val requests = mutableListOf<String>()

    /** 표 수를 요청한 작품 ID */
    val voteCountRequests: List<String>
        get() = requests.filter { it.startsWith("voteCount:") }.map { it.substringAfter(':') }

    fun put(artwork: FakeServerArtwork) {
        artworks[artwork.artwork.id] = artwork
    }

    /** 다른 사용자가 볼 수 있는 공개 작품 수 */
    val publishedCount: Int get() = artworks.values.count { it.published }

    fun stored(artworkId: String): FakeServerArtwork? = artworks[artworkId]

    fun setPublished(artworkId: String, published: Boolean) {
        artworks[artworkId] = requireNotNull(artworks[artworkId]).copy(published = published)
    }

    override suspend fun publishedPage(after: PublicArtworkCursor?, limit: Int): PublicArtworkPage {
        request("publishedPage", after?.value?.toString().orEmpty())
        val ordered = artworks.values.filter { it.published }
            .map { it.artwork }
            .sortedWith(compareByDescending<PublicArtwork> { it.createdAtMillis }.thenBy { it.id })
        val start = after?.let { cursor -> ordered.indexOfFirst { it.id == cursor.value } + 1 } ?: 0
        val items = ordered.drop(start).take(limit)
        return PublicArtworkPage(
            items = items,
            next = items.lastOrNull()?.takeIf {
                items.size >= limit
            }?.let { PublicArtworkCursor(it.id) }
        )
    }

    override suspend fun artwork(artworkId: String): PublicArtwork? {
        request("artwork", artworkId)
        return artworks[artworkId]?.takeIf { it.published }?.artwork
    }

    override suspend fun chunks(artworkId: String): List<PublicArtworkChunk> {
        request("chunks", artworkId)
        return visible(artworkId).chunks.sortedBy { it.index }
    }

    override suspend fun voteCount(artworkId: String): Long {
        request("voteCount", artworkId)
        return visible(artworkId).voters.size.toLong()
    }

    override suspend fun hasVoted(artworkId: String, uid: String): Boolean {
        request("hasVoted", artworkId)
        return uid in visible(artworkId).voters
    }

    override suspend fun vote(artworkId: String, uid: String) {
        request("vote", artworkId)
        val artwork = visible(artworkId)
        if (uid in artwork.voters) denied()
        artworks[artworkId] = artwork.copy(voters = artwork.voters + uid)
        if (loseNextVoteResponse) {
            loseNextVoteResponse = false
            throw GalleryStoreException(GalleryFailure.NETWORK, "응답을 받지 못했습니다.")
        }
    }

    override suspend fun unvote(artworkId: String, uid: String) {
        request("unvote", artworkId)
        val artwork = artworks[artworkId] ?: denied()
        artworks[artworkId] = artwork.copy(voters = artwork.voters - uid)
    }

    fun reset() {
        artworks.clear()
        networkFailure = false
        loseNextVoteResponse = false
        requests.clear()
    }

    private fun request(name: String, artworkId: String) {
        requests += "$name:$artworkId"
        if (networkFailure) throw GalleryStoreException(GalleryFailure.NETWORK, "연결 실패")
    }

    /** 다른 사용자가 볼 수 있는 작품. 비공개 작품은 서버 규칙이 거부한다. */
    private fun visible(artworkId: String): FakeServerArtwork =
        artworks[artworkId]?.takeIf { it.published }
            ?: throw GalleryStoreException(GalleryFailure.NOT_PUBLISHED, "공개 중인 작품이 아닙니다.")

    private fun denied(): Nothing =
        throw GalleryStoreException(GalleryFailure.UNKNOWN, "서버 규칙이 요청을 거부했습니다.")
}
