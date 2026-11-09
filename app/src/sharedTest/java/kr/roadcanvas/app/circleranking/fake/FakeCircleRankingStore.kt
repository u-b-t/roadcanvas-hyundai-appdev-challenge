package kr.roadcanvas.app.circleranking.fake

import kr.roadcanvas.app.circleranking.CircleRankingEntry
import kr.roadcanvas.app.circleranking.CircleRankingFailure
import kr.roadcanvas.app.circleranking.CircleRankingStore
import kr.roadcanvas.app.circleranking.CircleRankingStoreException

/**
 * 메모리에 리더보드 문서를 두는 대체 저장소. 서버 계약과 같은 순서로 돌려준다.
 * [networkFailure]이면 모든 요청이, [countFailure]이면 순위 집계만 연결 실패다.
 */
class FakeCircleRankingStore : CircleRankingStore {
    private val entries = mutableMapOf<String, CircleRankingEntry>()
    var networkFailure = false
    var countFailure = false

    /** 등록 순서와 관계없이 점수와 달성 시각으로 정렬된다. 같은 UID면 바꾼다. */
    fun put(entry: CircleRankingEntry) {
        entries[entry.uid] = entry
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

    fun reset() {
        entries.clear()
        networkFailure = false
        countFailure = false
    }

    private fun check() {
        if (networkFailure) unavailable()
    }

    private fun unavailable(): Nothing =
        throw CircleRankingStoreException(CircleRankingFailure.NETWORK, "테스트 연결 실패")
}
