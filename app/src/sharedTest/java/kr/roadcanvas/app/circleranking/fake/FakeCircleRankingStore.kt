package kr.roadcanvas.app.circleranking.fake

import kr.roadcanvas.app.artwork.ArtworkChunk
import kr.roadcanvas.app.artwork.artworkChunksOf
import kr.roadcanvas.app.circleranking.CircleRankingEntry
import kr.roadcanvas.app.circleranking.CircleRankingFailure
import kr.roadcanvas.app.circleranking.CircleRankingStore
import kr.roadcanvas.app.circleranking.CircleRankingStoreException
import kr.roadcanvas.app.circleranking.CircleRecordDrawing
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.recording.pathDistanceMeters

/**
 * 메모리에 리더보드 문서와 그 그림·경로 청크를 두는 대체 저장소. 서버 계약과 같은 순서로 돌려준다.
 * [networkFailure]이면 모든 요청이, [countFailure]이면 순위 집계만, [chunkFailure]이면 경로 청크 읽기만 연결 실패다.
 */
class FakeCircleRankingStore : CircleRankingStore {
    private val entries = mutableMapOf<String, CircleRankingEntry>()
    private val drawings = mutableMapOf<Pair<String, String>, CircleRecordDrawing>()
    private val chunks = mutableMapOf<Pair<String, String>, List<ArtworkChunk>>()
    var networkFailure = false
    var countFailure = false
    var chunkFailure = false

    /** 등록 순서와 관계없이 점수와 달성 시각으로 정렬된다. 같은 UID면 바꾼다. */
    fun put(entry: CircleRankingEntry) {
        entries[entry.uid] = entry
    }

    /**
     * [entry]를 순위에 두고 그 최고 기록 그림을 [path]로 저장한다. 경로는 [pointsPerChunk]개씩 청크로 나누며,
     * [missingChunk] index의 청크는 빼고 저장해 청크가 빠진 기록을 만든다.
     */
    fun putRecord(
        entry: CircleRankingEntry,
        path: List<RoutePoint>,
        pointsPerChunk: Int = 20,
        missingChunk: Int? = null
    ) {
        val drawingId = entry.drawingId ?: "drawing-${entry.uid}"
        put(entry.copy(drawingId = drawingId))
        val all = artworkChunksOf(path, pointsPerChunk)
        drawings[entry.uid to drawingId] = CircleRecordDrawing(
            finishedAtMillis = path.lastOrNull()?.timeMillis ?: entry.achievedAtMillis,
            distanceMeters = pathDistanceMeters(path),
            durationMillis = if (path.isEmpty()) {
                0L
            } else {
                path.last().timeMillis -
                    path.first().timeMillis
            },
            pointChunkCount = all.size
        )
        chunks[entry.uid to drawingId] = all.filter { it.index != missingChunk }.shuffled()
    }

    override suspend fun topEntries(limit: Int): List<CircleRankingEntry> {
        check()
        return entries.values
            .sortedWith(
                compareByDescending<CircleRankingEntry> { it.score }
                    .thenBy { it.achievedAtMillis }
                    .thenBy { it.uid }
            )
            .take(limit)
    }

    override suspend fun entry(uid: String): CircleRankingEntry? {
        check()
        return entries[uid]
    }

    override suspend fun countAbove(score: Int): Long {
        check()
        if (countFailure) unavailable()
        return entries.values.count { it.score > score }.toLong()
    }

    override suspend fun drawing(uid: String, drawingId: String): CircleRecordDrawing? {
        check()
        return drawings[uid to drawingId]
    }

    override suspend fun chunks(uid: String, drawingId: String): List<ArtworkChunk> {
        check()
        if (chunkFailure) unavailable()
        return chunks[uid to drawingId].orEmpty()
    }

    fun reset() {
        entries.clear()
        drawings.clear()
        chunks.clear()
        networkFailure = false
        countFailure = false
        chunkFailure = false
    }

    private fun check() {
        if (networkFailure) unavailable()
    }

    private fun unavailable(): Nothing =
        throw CircleRankingStoreException(CircleRankingFailure.NETWORK, "테스트 연결 실패")
}
