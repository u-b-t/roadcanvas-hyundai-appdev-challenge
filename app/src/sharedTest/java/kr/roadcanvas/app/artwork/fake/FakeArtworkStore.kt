package kr.roadcanvas.app.artwork.fake

import kr.roadcanvas.app.artwork.ArtworkAuthor
import kr.roadcanvas.app.artwork.ArtworkAuthorProvider
import kr.roadcanvas.app.artwork.ArtworkChunk
import kr.roadcanvas.app.artwork.ArtworkDraft
import kr.roadcanvas.app.artwork.ArtworkStore
import kr.roadcanvas.app.artwork.ArtworkStoreException
import kr.roadcanvas.app.artwork.ArtworkVisibility
import kr.roadcanvas.app.artwork.PublishFailure

/** 서버에 저장된 작품 문서와 청크 */
data class FakeArtwork(
    val ownerUid: String,
    val draft: ArtworkDraft,
    val visibility: ArtworkVisibility,
    val chunks: Map<Int, String>,
    /** 문서를 처음 만든 횟수 순서. 같은 문서를 다시 쓰면 바뀌지 않는다. */
    val createdOrder: Int
)

/**
 * 메모리에 작품을 보관하는 서버 저장소. 서버 규칙처럼 소유자만 초안을 바꾸고 공개 중에는 청크를 바꾸지 못한다.
 * [networkFailure]를 지정하면 모든 요청이 연결 실패하고, [failChunkAt]의 청크 저장은 한 번 실패하며,
 * [dropChunkAt]의 청크는 저장에 성공한 것처럼 응답하지만 한 번 저장되지 않는다.
 */
class FakeArtworkStore : ArtworkStore {
    private val artworks = mutableMapOf<String, FakeArtwork>()
    private var created = 0

    var networkFailure: Boolean = false
    var failChunkAt: Int? = null
    var dropChunkAt: Int? = null

    /** 지금까지 받은 요청 이름 */
    val requests = mutableListOf<String>()

    val all: Map<String, FakeArtwork> get() = artworks.toMap()

    fun artwork(artworkId: String): FakeArtwork? = artworks[artworkId]

    override suspend fun saveDraft(author: ArtworkAuthor, artworkId: String, draft: ArtworkDraft) {
        request("saveDraft")
        val existing = artworks[artworkId]
        if (existing != null && existing.ownerUid != author.uid) denied()
        artworks[artworkId] = existing?.copy(draft = draft, visibility = ArtworkVisibility.DRAFT)
            ?: FakeArtwork(author.uid, draft, ArtworkVisibility.DRAFT, emptyMap(), ++created)
    }

    override suspend fun saveChunk(artworkId: String, chunk: ArtworkChunk) {
        request("saveChunk")
        val artwork = artworks[artworkId] ?: denied()
        if (artwork.visibility == ArtworkVisibility.PUBLISHED) denied()
        if (failChunkAt == chunk.index) {
            failChunkAt = null
            throw ArtworkStoreException(PublishFailure.NETWORK, "청크 ${chunk.index} 저장 실패")
        }
        if (dropChunkAt == chunk.index) {
            dropChunkAt = null
            return
        }
        artworks[artworkId] =
            artwork.copy(chunks = artwork.chunks + (chunk.index to chunk.pointsJson))
    }

    override suspend fun chunkIndices(artworkId: String): List<Int> {
        request("chunkIndices")
        return artworks[artworkId]?.chunks?.keys?.toList() ?: denied()
    }

    override suspend fun setVisibility(artworkId: String, visibility: ArtworkVisibility) {
        request("setVisibility")
        val artwork = artworks[artworkId] ?: denied()
        artworks[artworkId] = artwork.copy(visibility = visibility)
    }

    override suspend fun updateTitle(artworkId: String, title: String) {
        request("updateTitle")
        val artwork = artworks[artworkId] ?: denied()
        artworks[artworkId] = artwork.copy(draft = artwork.draft.copy(title = title))
    }

    fun reset() {
        artworks.clear()
        created = 0
        networkFailure = false
        failChunkAt = null
        dropChunkAt = null
        requests.clear()
    }

    private fun request(name: String) {
        requests += name
        if (networkFailure) throw ArtworkStoreException(PublishFailure.NETWORK, "연결 실패")
    }

    private fun denied(): Nothing =
        throw ArtworkStoreException(PublishFailure.UNKNOWN, "서버 규칙이 요청을 거부했습니다.")
}

/** 테스트 작성자. [failure]를 지정하면 인증에 실패한다. */
class FakeArtworkAuthorProvider : ArtworkAuthorProvider {
    var author = DEFAULT_AUTHOR
    var failure: Throwable? = null

    override suspend fun current(): ArtworkAuthor {
        failure?.let { throw it }
        return author
    }

    fun reset() {
        author = DEFAULT_AUTHOR
        failure = null
    }

    companion object {
        val DEFAULT_AUTHOR = ArtworkAuthor(uid = "test-uid", nickname = "드라이버-test")
    }
}
