package kr.roadcanvas.app.artwork

/** 작품을 공개하는 작성자. [uid]는 인증 사용자 UID이고 [nickname]은 작품에 보일 닉네임이다. */
data class ArtworkAuthor(val uid: String, val nickname: String)

/** 공개에 쓸 현재 작성자를 준다. 인증하지 못하면 예외를 던진다. */
fun interface ArtworkAuthorProvider {
    suspend fun current(): ArtworkAuthor
}

/** 서버 작품 문서의 공개 범위 */
enum class ArtworkVisibility { DRAFT, PUBLISHED }

/** 작품 문서에 쓰는 값. 거리는 미터, 시간은 밀리초다. */
data class ArtworkDraft(
    val nickname: String,
    val title: String,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointChunkCount: Int
)

/** 경로 청크 하나. [pointsJson]은 서버 명세의 좌표 JSON 배열 문자열이다. */
data class ArtworkChunk(val index: Int, val pointsJson: String)

/**
 * 공개 작품 서버 저장소. 서버 계약은 docs/firebase-artwork-client.md를 따른다.
 * 실패하면 [ArtworkStoreException]이나 다른 예외를 던진다.
 */
interface ArtworkStore {
    /**
     * [artworkId] 문서를 [author] 소유의 DRAFT로 만든다. 이미 있으면 생성 시각과 소유자는 두고
     * 나머지 값을 바꾼 뒤 DRAFT로 둔다.
     */
    suspend fun saveDraft(author: ArtworkAuthor, artworkId: String, draft: ArtworkDraft)

    /** 경로 청크를 저장한다. 같은 [ArtworkChunk.index]는 같은 청크 문서를 덮어쓴다. */
    suspend fun saveChunk(artworkId: String, chunk: ArtworkChunk)

    /** 저장된 청크의 index 목록 */
    suspend fun chunkIndices(artworkId: String): List<Int>

    suspend fun setVisibility(artworkId: String, visibility: ArtworkVisibility)

    suspend fun updateTitle(artworkId: String, title: String)
}

/** 서버 저장소 오류를 공개 실패 이유로 알린다. */
class ArtworkStoreException(
    val failure: PublishFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** 익명 인증이 연결되기 전에 쓰는 작성자. 항상 인증 실패다. */
object UnavailableArtworkAuthorProvider : ArtworkAuthorProvider {
    override suspend fun current(): ArtworkAuthor =
        throw ArtworkStoreException(PublishFailure.AUTH, "익명 인증이 연결되지 않았습니다.")
}

/** 서버 연동 전에 쓰는 저장소. 모든 요청이 연결 실패다. */
object UnavailableArtworkStore : ArtworkStore {
    private fun unavailable(): Nothing =
        throw ArtworkStoreException(PublishFailure.NETWORK, "작품 서버가 연결되지 않았습니다.")

    override suspend fun saveDraft(author: ArtworkAuthor, artworkId: String, draft: ArtworkDraft) =
        unavailable()

    override suspend fun saveChunk(artworkId: String, chunk: ArtworkChunk) = unavailable()

    override suspend fun chunkIndices(artworkId: String): List<Int> = unavailable()

    override suspend fun setVisibility(artworkId: String, visibility: ArtworkVisibility) =
        unavailable()

    override suspend fun updateTitle(artworkId: String, title: String) = unavailable()
}
