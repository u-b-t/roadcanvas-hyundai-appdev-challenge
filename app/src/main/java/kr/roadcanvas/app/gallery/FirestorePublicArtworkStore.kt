package kr.roadcanvas.app.gallery

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

private const val ARTWORKS = "artworks"
private const val CHUNKS = "chunks"
private const val VOTES = "votes"
private const val PUBLISHED = "PUBLISHED"

/**
 * Firestore 공개 작품 저장소. 서버 계약은 docs/firebase-artwork-client.md와 docs/firebase-client-queries.md를 따른다.
 * 오래된 캐시를 최신 결과처럼 보이지 않도록 모든 조회는 서버에서 읽는다.
 * 다른 사용자의 비공개 작품은 보안 규칙이 읽기를 거부하므로 비공개로 바뀐 작품으로 본다.
 */
class FirestorePublicArtworkStore(
    private val db: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : PublicArtworkStore {
    override suspend fun publishedPage(after: PublicArtworkCursor?, limit: Int): PublicArtworkPage =
        request(denied = GalleryFailure.AUTH) {
            var query = db().collection(ARTWORKS)
                .whereEqualTo("visibility", PUBLISHED)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            (after?.value as? DocumentSnapshot)?.let { query = query.startAfter(it) }
            val documents = query.get(Source.SERVER).await().documents
            PublicArtworkPage(
                // 서버 규칙을 통과했지만 화면에 쓸 수 없는 문서는 건너뛴다.
                items = documents.mapNotNull { it.toPublicArtwork() },
                next = documents.lastOrNull()
                    ?.takeIf { documents.size >= limit }
                    ?.let(::PublicArtworkCursor)
            )
        }

    override suspend fun artwork(artworkId: String): PublicArtwork? = try {
        request { artworkDocument(artworkId).get(Source.SERVER).await().toPublicArtwork() }
    } catch (e: GalleryStoreException) {
        if (e.failure == GalleryFailure.NOT_PUBLISHED) null else throw e
    }

    override suspend fun chunks(artworkId: String): List<PublicArtworkChunk> = request {
        artworkDocument(artworkId).collection(CHUNKS)
            .orderBy("index")
            .get(Source.SERVER)
            .await()
            .documents
            .map { chunk ->
                PublicArtworkChunk(
                    index = chunk.getLong("index")?.toInt() ?: invalidChunk(chunk),
                    pointsJson = chunk.getString("pointsJson") ?: invalidChunk(chunk)
                )
            }
    }

    override suspend fun voteCount(artworkId: String): Long = request {
        artworkDocument(artworkId).collection(VOTES).count()
            .get(AggregateSource.SERVER)
            .await()
            .count
    }

    override suspend fun hasVoted(artworkId: String, uid: String): Boolean = request {
        voteDocument(artworkId, uid).get(Source.SERVER).await().exists()
    }

    override suspend fun vote(artworkId: String, uid: String) = request {
        voteDocument(artworkId, uid).set(mapOf("createdAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }

    override suspend fun unvote(artworkId: String, uid: String) = request {
        voteDocument(artworkId, uid).delete().await()
        Unit
    }

    private fun artworkDocument(artworkId: String) = db().collection(ARTWORKS).document(artworkId)

    private fun voteDocument(artworkId: String, uid: String) =
        artworkDocument(artworkId).collection(VOTES).document(uid)

    /** Firestore 오류를 조회 실패 이유로 바꾼다. 규칙이 거부하면 [denied]로 본다. */
    private suspend fun <T> request(
        denied: GalleryFailure = GalleryFailure.NOT_PUBLISHED,
        block: suspend () -> T
    ): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        val failure = when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> GalleryFailure.NETWORK
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> GalleryFailure.AUTH
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> denied
            FirebaseFirestoreException.Code.NOT_FOUND -> GalleryFailure.NOT_PUBLISHED
            else -> GalleryFailure.UNKNOWN
        }
        throw GalleryStoreException(failure, "Firestore 요청 실패: ${e.code}", e)
    }

    private fun invalidChunk(chunk: DocumentSnapshot): Nothing = throw GalleryStoreException(
        GalleryFailure.INVALID_ROUTE,
        "청크 ${chunk.id}의 형식이 맞지 않습니다."
    )
}

/** 공개 중이고 필수 필드가 모두 있으면 공개 작품으로 읽는다. */
private fun DocumentSnapshot.toPublicArtwork(): PublicArtwork? {
    if (!exists() || getString("visibility") != PUBLISHED) return null
    return PublicArtwork(
        id = id,
        title = getString("title") ?: return null,
        nickname = getString("nickname") ?: return null,
        distanceMeters = getDouble("distanceMeters") ?: return null,
        durationMillis = getLong("durationMillis") ?: return null,
        pointChunkCount = getLong("pointChunkCount")?.toInt() ?: return null,
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: return null
    )
}
