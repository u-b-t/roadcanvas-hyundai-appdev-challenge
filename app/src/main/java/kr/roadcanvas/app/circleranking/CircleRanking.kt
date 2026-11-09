package kr.roadcanvas.app.circleranking

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kr.roadcanvas.app.challenge.ChallengeResult
import kr.roadcanvas.app.challenge.challengeResultOf
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.gallery.GalleryStoreException
import kr.roadcanvas.app.gallery.decodeArtworkRoute

/** 순위표에 보이는 사용자 수 */
const val CIRCLE_RANKING_SIZE = 10

/** 읽기 전 기록 등록을 기다리는 최대 시간. 오프라인이면 쓰기가 끝나지 않으므로 기다리지 않고 순위를 읽는다. */
const val CIRCLE_RANKING_SYNC_TIMEOUT_MILLIS = 10_000L

/** 순위표 한 줄. [rank]는 1부터이며 같은 점수는 같은 순위다. [mine]이면 현재 사용자다. [uid]로 기록 상세를 연다. */
data class CircleRank(
    val uid: String,
    val rank: Int,
    val nickname: String,
    val score: Int,
    val achievedAtMillis: Long,
    val mine: Boolean
)

/** 내 최고 기록. 순위를 집계하지 못했으면 [rank]가 null이다. [inTop]이면 순위표 안에 있다. [uid]는 내 UID다. */
data class MyCircleRecord(
    val uid: String,
    val rank: Int?,
    val score: Int,
    val achievedAtMillis: Long,
    val inTop: Boolean
)

/** 순위표와 내 기록. 내 기록이 서버에 없으면 [mine]이 null이다. */
data class CircleRankingBoard(val top: List<CircleRank>, val mine: MyCircleRecord?)

/** 순위 조회 결과. 실패하면 예외 대신 [Failure]로 전달한다. */
sealed interface CircleRankingResult {
    data class Success(val board: CircleRankingBoard) : CircleRankingResult

    data class Failure(val reason: CircleRankingFailure) : CircleRankingResult
}

/**
 * 순위에 오른 최고 기록의 상세. [score]는 순위의 점수이고, 점수 구성과 맞춤 원은 [result]가 서버 경로로 다시 계산한 값이다.
 * [rank]는 집계하지 못하면 null이다. [mine]이면 현재 사용자의 기록이다.
 */
data class CircleRecordDetail(
    val nickname: String,
    val rank: Int?,
    val score: Int,
    val achievedAtMillis: Long,
    val mine: Boolean,
    val result: ChallengeResult
)

/** 기록 상세 조회 결과. 실패하면 예외 대신 [Failure]로 전달한다. */
sealed interface CircleRecordResult {
    data class Success(val detail: CircleRecordDetail) : CircleRecordResult

    data class Failure(val reason: CircleRankingFailure) : CircleRecordResult
}

/**
 * 동그라미 챌린지 순위를 [store]에서 읽는다. [currentUid]는 익명 인증된 사용자 UID이며 인증 전이면 null이다.
 * [syncRecords]는 읽기 전에 기기의 완료 기록을 서버에 등록해 방금 끝낸 도전도 순위에 들어가게 한다.
 * 등록에 실패하거나 [CIRCLE_RANKING_SYNC_TIMEOUT_MILLIS] 안에 끝나지 않아도 서버에 있는 순위는 보여준다.
 */
class CircleRanking(
    private val store: CircleRankingStore,
    private val currentUid: () -> String?,
    private val syncRecords: suspend () -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {}
) {
    /**
     * 상위 [CIRCLE_RANKING_SIZE]명과 내 기록. 같은 점수는 앞 사람과 같은 순위다.
     * 내 기록이 순위표 밖이면 내 점수보다 높은 문서 수로 순위를 구하고, 집계하지 못하면 순위만 비워 둔다.
     */
    suspend fun load(): CircleRankingResult = try {
        val uid = currentUid() ?: throw CircleRankingStoreException(
            CircleRankingFailure.AUTH,
            "익명 계정을 확인하지 못했습니다."
        )
        attemptSync()
        val entries = store.topEntries(CIRCLE_RANKING_SIZE)
        val top = mutableListOf<CircleRank>()
        entries.forEachIndexed { index, entry ->
            val previous = top.lastOrNull()
            val rank = if (previous != null && previous.score == entry.score) {
                previous.rank
            } else {
                index + 1
            }
            top +=
                CircleRank(
                    entry.uid,
                    rank,
                    entry.nickname,
                    entry.score,
                    entry.achievedAtMillis,
                    entry.uid == uid
                )
        }
        val mineInTop = top.firstOrNull { it.mine }
        val mine = if (mineInTop != null) {
            MyCircleRecord(
                uid,
                mineInTop.rank,
                mineInTop.score,
                mineInTop.achievedAtMillis,
                inTop = true
            )
        } else {
            store.entry(uid)?.let {
                MyCircleRecord(uid, rankOf(it.score), it.score, it.achievedAtMillis, inTop = false)
            }
        }
        CircleRankingResult.Success(CircleRankingBoard(top, mine))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        CircleRankingResult.Failure(failureOf(e))
    }

    /**
     * [uid] 사용자의 순위 기록(최고 기록)과 그 그림의 경로. 청크는 index가 0부터 그림 문서의 청크 수만큼 한 번씩 있어야 하며,
     * 빠지거나 형식이 맞지 않으면 [CircleRankingFailure.INVALID_ROUTE]다. 순위 기록이나 그림이 없으면 [CircleRankingFailure.NOT_FOUND]다.
     */
    suspend fun detail(uid: String): CircleRecordResult = try {
        val me = currentUid() ?: throw CircleRankingStoreException(
            CircleRankingFailure.AUTH,
            "익명 계정을 확인하지 못했습니다."
        )
        val entry = store.entry(uid) ?: notFound("순위 기록이 없습니다.")
        val drawingId = entry.drawingId ?: notFound("순위 기록에 그림 ID가 없습니다.")
        val drawing = store.drawing(uid, drawingId) ?: notFound("그림 문서가 없습니다.")
        val path = try {
            decodeArtworkRoute(store.chunks(uid, drawingId), drawing.pointChunkCount)
        } catch (e: GalleryStoreException) {
            throw CircleRankingStoreException(CircleRankingFailure.INVALID_ROUTE, "경로 오류", e)
        }
        if (path.isEmpty()) {
            throw CircleRankingStoreException(CircleRankingFailure.INVALID_ROUTE, "경로가 없습니다.")
        }
        val result = challengeResultOf(
            CompletedDrawing(
                mode = DrawingMode.CIRCLE,
                completedAtMillis = drawing.finishedAtMillis,
                path = path,
                score = entry.score,
                metrics = emptyMap(),
                distanceMeters = drawing.distanceMeters,
                durationMillis = drawing.durationMillis
            )
        )
        CircleRecordResult.Success(
            CircleRecordDetail(
                nickname = entry.nickname,
                rank = rankOf(entry.score),
                score = entry.score,
                achievedAtMillis = entry.achievedAtMillis,
                mine = uid == me,
                result = result
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        CircleRecordResult.Failure(failureOf(e))
    }

    private fun notFound(message: String): Nothing =
        throw CircleRankingStoreException(CircleRankingFailure.NOT_FOUND, message)

    private suspend fun attemptSync() = try {
        withTimeoutOrNull(CIRCLE_RANKING_SYNC_TIMEOUT_MILLIS) { syncRecords() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }

    private suspend fun rankOf(score: Int): Int? = try {
        (store.countAbove(score) + 1).toInt()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        null
    }

    private fun failureOf(error: Throwable): CircleRankingFailure = when (error) {
        is CircleRankingStoreException -> error.failure
        is IOException -> CircleRankingFailure.NETWORK
        else -> CircleRankingFailure.UNKNOWN
    }
}
