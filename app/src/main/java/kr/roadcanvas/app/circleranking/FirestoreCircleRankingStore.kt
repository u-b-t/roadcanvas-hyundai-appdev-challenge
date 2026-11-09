package kr.roadcanvas.app.circleranking

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kr.roadcanvas.app.artwork.ArtworkChunk

/** Firestore 요청 하나를 기다리는 최대 시간. 응답이 없으면 연결 실패로 본다. */
private const val REQUEST_TIMEOUT_MILLIS = 20_000L

/**
 * Firestore `leaderboards/circle/entries` 저장소. 오래된 캐시를 최신 순위처럼 보이지 않도록 서버에서만 읽는다.
 * 정렬은 `score` 내림차순, `achievedAt` 오름차순이며, 마지막 동점은 Firestore가 문서 ID(UID) 오름차순으로 정한다.
 */
class FirestoreCircleRankingStore(
    private val db: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : CircleRankingStore {
    private fun entries() = db().collection("leaderboards").document("circle").collection("entries")

    override suspend fun topEntries(limit: Int): List<CircleRankingEntry> = request {
        entries()
            .orderBy("score", Query.Direction.DESCENDING)
            .orderBy("achievedAt", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get(Source.SERVER)
            .awaitResult()
            .documents
            // 형식이 맞지 않는 문서는 건너뛴다.
            .mapNotNull { it.toEntry() }
    }

    override suspend fun entry(uid: String): CircleRankingEntry? = request {
        entries().document(uid).get(Source.SERVER).awaitResult().toEntry()
    }

    override suspend fun countAbove(score: Int): Long = request {
        entries().whereGreaterThan("score", score).count()
            .get(AggregateSource.SERVER)
            .awaitResult()
            .count
    }

    override suspend fun drawing(uid: String, drawingId: String): CircleRecordDrawing? = request {
        drawingDocument(uid, drawingId).get(Source.SERVER).awaitResult().toDrawing()
    }

    override suspend fun chunks(uid: String, drawingId: String): List<ArtworkChunk> = request {
        drawingDocument(uid, drawingId).collection("chunks")
            .get(Source.SERVER)
            .awaitResult()
            .documents
            .map { chunk ->
                ArtworkChunk(
                    index = chunk.getLong("index")?.toInt() ?: invalidChunk(chunk),
                    pointsJson = chunk.getString("pointsJson") ?: invalidChunk(chunk)
                )
            }
    }

    private fun drawingDocument(uid: String, drawingId: String) =
        db().collection("users").document(uid).collection("drawings").document(drawingId)

    private fun invalidChunk(chunk: DocumentSnapshot): Nothing = throw CircleRankingStoreException(
        CircleRankingFailure.INVALID_ROUTE,
        "경로 청크 ${chunk.id}의 형식이 올바르지 않습니다."
    )

    /** Firestore 오류를 조회 실패 이유로 바꾼다. */
    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        val failure = when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED -> CircleRankingFailure.NETWORK
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> CircleRankingFailure.AUTH
            FirebaseFirestoreException.Code.NOT_FOUND -> CircleRankingFailure.NOT_FOUND
            else -> CircleRankingFailure.UNKNOWN
        }
        throw CircleRankingStoreException(failure, "Firestore 요청 실패: ${e.code}", e)
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { await() }
            ?: throw CircleRankingStoreException(
                CircleRankingFailure.NETWORK,
                "Firestore 응답이 없습니다."
            )
}

private fun DocumentSnapshot.toEntry(): CircleRankingEntry? {
    if (!exists()) return null
    return CircleRankingEntry(
        uid = id,
        nickname = getString("nickname") ?: return null,
        score = getLong("score")?.toInt() ?: return null,
        achievedAtMillis = getTimestamp("achievedAt")?.toDate()?.time ?: return null,
        drawingId = getString("drawingId")
    )
}

private fun DocumentSnapshot.toDrawing(): CircleRecordDrawing? {
    if (!exists()) return null
    return CircleRecordDrawing(
        finishedAtMillis = getTimestamp("finishedAt")?.toDate()?.time ?: return null,
        distanceMeters = getDouble("distanceMeters") ?: return null,
        durationMillis = getLong("durationMillis") ?: return null,
        pointChunkCount = getLong("pointChunkCount")?.toInt() ?: return null
    )
}
