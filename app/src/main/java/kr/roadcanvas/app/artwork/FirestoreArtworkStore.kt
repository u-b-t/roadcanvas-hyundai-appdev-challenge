package kr.roadcanvas.app.artwork

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Firestore 요청 하나를 기다리는 최대 시간. 오프라인이면 쓰기가 끝나지 않으므로 연결 실패로 본다. */
private const val REQUEST_TIMEOUT_MILLIS = 20_000L

/**
 * `artworks/{artworkId}`와 `chunks`에 작품을 쓰는 Firestore 구현. 서버 계약은 docs/firebase-artwork-client.md다.
 *
 * 규칙상 없는 작품 문서는 읽을 수 없고 생성 시각은 바꿀 수 없으므로, 초안은 먼저 새 문서로 만들어 보고
 * 이미 있으면 생성 시각과 소유자를 뺀 값만 바꾼다. 청크 확인은 캐시가 아닌 서버에서 읽는다.
 */
class FirestoreArtworkStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : ArtworkStore {
    override suspend fun saveDraft(author: ArtworkAuthor, artworkId: String, draft: ArtworkDraft) {
        val values = mapOf(
            "nickname" to draft.nickname,
            "title" to draft.title,
            "visibility" to ArtworkVisibility.DRAFT.name,
            "pointChunkCount" to draft.pointChunkCount,
            "distanceMeters" to draft.distanceMeters,
            "durationMillis" to draft.durationMillis
        )
        val document = artwork(artworkId)
        val created = try {
            request {
                document.set(
                    values + mapOf(
                        "ownerUid" to author.uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            true
        } catch (e: ArtworkStoreException) {
            if (e.failure == PublishFailure.NETWORK) throw e
            false
        }
        if (!created) request { document.update(values) }
    }

    override suspend fun saveChunk(artworkId: String, chunk: ArtworkChunk) {
        request {
            artwork(artworkId).collection(CHUNKS).document(chunk.index.toString())
                .set(mapOf("index" to chunk.index, "pointsJson" to chunk.pointsJson))
        }
    }

    override suspend fun chunkIndices(artworkId: String): List<Int> {
        val snapshot = request { artwork(artworkId).collection(CHUNKS).get(Source.SERVER) }
        return snapshot.documents.map {
            it.getLong("index")?.toInt()
                ?: throw ArtworkStoreException(PublishFailure.INCOMPLETE, "청크 index가 없습니다.")
        }
    }

    override suspend fun setVisibility(artworkId: String, visibility: ArtworkVisibility) {
        request { artwork(artworkId).update("visibility", visibility.name) }
    }

    override suspend fun updateTitle(artworkId: String, title: String) {
        request { artwork(artworkId).update("title", title) }
    }

    private fun artwork(artworkId: String) = firestore().collection(ARTWORKS).document(artworkId)

    /** 요청을 기다리고, 시간 초과와 Firestore 오류를 공개 실패 이유로 바꾼다. */
    private suspend fun <T> request(block: () -> Task<T>): T {
        val result = try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { Result.success(block().await()) }
        } catch (e: FirebaseFirestoreException) {
            throw ArtworkStoreException(publishFailureOf(e.code), e.message ?: e.code.name, e)
        }
        // 쓰기 요청은 null로 끝나므로 결과 값이 아니라 감싼 Result로 시간 초과를 구분한다.
        result ?: throw ArtworkStoreException(PublishFailure.NETWORK, "Firestore 응답이 없습니다.")
        return result.getOrThrow()
    }

    private companion object {
        const val ARTWORKS = "artworks"
        const val CHUNKS = "chunks"
    }
}

/** Firestore 오류 코드를 공개 실패 이유로 바꾼다. */
fun publishFailureOf(code: FirebaseFirestoreException.Code): PublishFailure = when (code) {
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
    FirebaseFirestoreException.Code.ABORTED -> PublishFailure.NETWORK
    FirebaseFirestoreException.Code.UNAUTHENTICATED -> PublishFailure.AUTH
    FirebaseFirestoreException.Code.INVALID_ARGUMENT -> PublishFailure.INVALID
    else -> PublishFailure.UNKNOWN
}
