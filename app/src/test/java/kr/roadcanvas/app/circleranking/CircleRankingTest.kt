package kr.roadcanvas.app.circleranking

import java.time.ZoneId
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.circleranking.fake.FakeCircleRankingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleRankingTest {
    private val store = FakeCircleRankingStore()
    private var uid: String? = "me"
    private var syncs = 0
    private var sync: suspend () -> Unit = { syncs++ }
    private val ranking = CircleRanking(store, currentUid = { uid }, syncRecords = { sync() })

    private fun put(uid: String, score: Int, at: Long = 0L) =
        store.put(CircleRankingEntry(uid, "닉-$uid", score, at))

    private suspend fun board(): CircleRankingBoard =
        (ranking.load() as CircleRankingResult.Success).board

    @Test
    fun `같은 점수는 같은 순위이고 다음 점수는 그만큼 건너뛴다`() = runTest {
        put("a", 100, at = 2)
        put("b", 100, at = 1)
        put("c", 90)
        put("d", 80)

        val top = board().top

        assertEquals(listOf("b", "a", "c", "d").map { "닉-$it" }, top.map { it.nickname })
        assertEquals(listOf(1, 1, 3, 4), top.map { it.rank })
    }

    @Test
    fun `순위표는 10명까지이고 안에 있는 내 기록을 표시한다`() = runTest {
        (1..12).forEach { put("u$it", 100 - it) }
        put("me", 95)

        val board = board()

        assertEquals(CIRCLE_RANKING_SIZE, board.top.size)
        assertEquals(listOf("닉-me"), board.top.filter { it.mine }.map { it.nickname })
        assertEquals(
            MyCircleRecord(rank = 5, score = 95, achievedAtMillis = 0, inTop = true),
            board.mine
        )
    }

    @Test
    fun `10위 밖 내 순위는 내 점수보다 높은 기록 수로 구한다`() = runTest {
        (1..11).forEach { put("u$it", 90) }
        put("me", 90, at = 1)
        put("low", 10)

        val mine = board().mine

        // 90점 11명과 같은 점수이므로 공동 1위다.
        assertEquals(
            MyCircleRecord(rank = 1, score = 90, achievedAtMillis = 1, inTop = false),
            mine
        )
    }

    @Test
    fun `순위 집계에 실패하면 최고 점수만 두고 순위를 비운다`() = runTest {
        (1..11).forEach { put("u$it", 90) }
        put("me", 50)
        store.countFailure = true

        val mine = board().mine!!

        assertNull(mine.rank)
        assertEquals(50, mine.score)
        assertFalse(mine.inTop)
    }

    @Test
    fun `내 기록이 없으면 내 기록은 null이다`() = runTest {
        put("a", 10)

        assertNull(board().mine)
    }

    @Test
    fun `인증 전이면 인증 실패로 알린다`() = runTest {
        uid = null

        assertEquals(CircleRankingResult.Failure(CircleRankingFailure.AUTH), ranking.load())
    }

    @Test
    fun `서버에 연결하지 못하면 연결 실패로 알린다`() = runTest {
        store.networkFailure = true

        assertEquals(CircleRankingResult.Failure(CircleRankingFailure.NETWORK), ranking.load())
    }

    @Test
    fun `읽기 전에 기록을 등록하고 등록에 실패해도 순위를 읽는다`() = runTest {
        put("me", 70)
        sync = {
            syncs++
            error("등록 실패")
        }

        val board = board()

        assertEquals(1, syncs)
        assertEquals(70, board.mine?.score)
    }

    @Test
    fun `등록이 끝나지 않으면 기다리지 않고 순위를 읽는다`() = runTest {
        put("me", 70)
        sync = { awaitCancellation() }

        assertEquals(70, board().mine?.score)
        assertTrue(testScheduler.currentTime >= CIRCLE_RANKING_SYNC_TIMEOUT_MILLIS)
    }

    @Test
    fun `기록 날짜는 기기 시간대 기준 연월일이다`() {
        // 2026-11-08 15:30 UTC는 서울 기준 다음 날 0시 30분이다.
        val millis = 1_794_151_800_000L
        assertEquals("2026.11.09", formatRankingDate(millis, ZoneId.of("Asia/Seoul")))
        assertEquals("2026.11.08", formatRankingDate(millis, ZoneId.of("UTC")))
    }
}
