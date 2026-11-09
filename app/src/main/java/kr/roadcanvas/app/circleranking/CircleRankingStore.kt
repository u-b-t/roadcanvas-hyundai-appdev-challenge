package kr.roadcanvas.app.circleranking

import kr.roadcanvas.app.artwork.ArtworkChunk

/** 동그라미 챌린지 리더보드 문서 하나. 사용자마다 최고 점수 하나다. [drawingId]는 그 점수를 받은 그림 문서 ID다. */
data class CircleRankingEntry(
    val uid: String,
    val nickname: String,
    val score: Int,
    val achievedAtMillis: Long,
    val drawingId: String? = null
)

/** 순위 기록이 가리키는 그림 문서(`users/{uid}/drawings/{drawingId}`). 경로는 [pointChunkCount]개 청크로 나뉘어 있다. */
data class CircleRecordDrawing(
    val finishedAtMillis: Long,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointChunkCount: Int
)

/** 순위를 읽지 못한 이유. 화면이 안내 문구를 고르는 데 쓴다. */
enum class CircleRankingFailure {
    /** 서버에 연결하지 못했다. */
    NETWORK,

    /** 익명 계정을 확인하지 못했다. */
    AUTH,

    /** 서버가 요청을 거부했거나 알 수 없는 오류다. */
    UNKNOWN,

    /** 순위 기록이나 그 그림 문서가 없다. */
    NOT_FOUND,

    /** 경로 청크가 빠졌거나 순서·형식이 맞지 않는다. */
    INVALID_ROUTE
}

/** 서버 저장소 오류를 실패 이유로 알린다. */
class CircleRankingStoreException(
    val failure: CircleRankingFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 동그라미 챌린지 리더보드 서버 저장소. 서버 계약은 docs/firebase-client-queries.md의 동그라미 리더보드를 따른다.
 * 실패하면 [CircleRankingStoreException]이나 다른 예외를 던진다.
 */
interface CircleRankingStore {
    /** 점수 내림차순, 달성 시각 오름차순, UID 오름차순으로 [limit]개 */
    suspend fun topEntries(limit: Int): List<CircleRankingEntry>

    /** [uid] 사용자의 리더보드 문서. 등록한 점수가 없으면 null이다. */
    suspend fun entry(uid: String): CircleRankingEntry?

    /** 점수가 [score]보다 높은 문서 수(서버 `count()` 집계) */
    suspend fun countAbove(score: Int): Long

    /** [uid] 사용자의 [drawingId] 그림 문서. 없으면 null이다. 다른 사용자는 순위에 오른 그림만 읽을 수 있다. */
    suspend fun drawing(uid: String, drawingId: String): CircleRecordDrawing?

    /** [drawingId] 그림의 경로 청크. 개수와 순서는 호출한 쪽이 확인한다. */
    suspend fun chunks(uid: String, drawingId: String): List<ArtworkChunk>
}

/** 서버가 연결되지 않은 빌드에서 쓰는 저장소. 모든 요청이 연결 실패다. */
object UnavailableCircleRankingStore : CircleRankingStore {
    private fun unavailable(): Nothing = throw CircleRankingStoreException(
        CircleRankingFailure.NETWORK,
        "순위 서버가 연결되지 않았습니다."
    )

    override suspend fun topEntries(limit: Int) = unavailable()

    override suspend fun entry(uid: String) = unavailable()

    override suspend fun countAbove(score: Int) = unavailable()

    override suspend fun drawing(uid: String, drawingId: String) = unavailable()

    override suspend fun chunks(uid: String, drawingId: String) = unavailable()
}
