package kr.roadcanvas.app.circleranking

import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.challenge.fitCircle
import kr.roadcanvas.app.challenge.scoreComponents
import kr.roadcanvas.app.circleranking.fake.FakeCircleRankingStore
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.score.TestPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// 경로 청크의 JSON(org.json)을 읽으므로 Robolectric으로 실행한다.
@RunWith(RobolectricTestRunner::class)
class CircleRecordDetailTest {
    private val store = FakeCircleRankingStore()
    private var uid: String? = "me"
    private val ranking = CircleRanking(store, currentUid = { uid })

    private val circle = TestPaths.circle(50.0).mapIndexed { i, it ->
        RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
    }

    private fun entry(uid: String, score: Int) = CircleRankingEntry(uid, "닉-$uid", score, START)

    private suspend fun detail(uid: String): CircleRecordDetail =
        (ranking.detail(uid) as CircleRecordResult.Success).detail

    @Test
    fun `다른 사용자의 순위 기록 경로와 순위·점수를 읽고 점수 구성과 맞춤 원을 계산한다`() = runTest {
        store.putRecord(entry("a", 95), circle)
        store.putRecord(entry("b", 80), circle.take(30))

        val detail = detail("b")

        assertEquals("닉-b", detail.nickname)
        assertEquals(2, detail.rank)
        assertEquals(80, detail.score)
        assertFalse(detail.mine)
        assertEquals(circle.take(30), detail.result.path)
        assertEquals(29_000L, detail.result.durationMillis)
        assertEquals(3, detail.result.scoreComponents.size)
        assertNotNull(detail.result.fitCircle)
    }

    @Test
    fun `여러 청크를 순서대로 이어 전체 경로를 만든다`() = runTest {
        store.putRecord(entry("a", 90), circle, pointsPerChunk = 7)

        val detail = detail("a")

        assertEquals(circle, detail.result.path)
        // 반지름 50m 원이므로 맞춤 원도 반지름 약 50m다.
        assertEquals(50.0, detail.result.fitCircle!!.radiusMeters, 1.0)
    }

    @Test
    fun `내 기록이면 내 기록으로 표시한다`() = runTest {
        store.putRecord(entry("me", 70), circle)

        assertTrue(detail("me").mine)
    }

    @Test
    fun `청크가 하나라도 빠지면 경로 오류로 알린다`() = runTest {
        store.putRecord(entry("a", 90), circle, pointsPerChunk = 10, missingChunk = 2)

        assertEquals(
            CircleRecordResult.Failure(CircleRankingFailure.INVALID_ROUTE),
            ranking.detail("a")
        )
    }

    @Test
    fun `경로 청크를 읽지 못하면 연결 실패로 알린다`() = runTest {
        store.putRecord(entry("a", 90), circle)
        store.chunkFailure = true

        assertEquals(
            CircleRecordResult.Failure(CircleRankingFailure.NETWORK),
            ranking.detail("a")
        )
    }

    @Test
    fun `순위 기록이나 그림이 없으면 찾지 못했다고 알린다`() = runTest {
        store.put(entry("noDrawing", 50).copy(drawingId = "gone"))
        store.put(entry("noId", 40))

        listOf("absent", "noDrawing", "noId").forEach {
            assertEquals(
                CircleRecordResult.Failure(CircleRankingFailure.NOT_FOUND),
                ranking.detail(it)
            )
        }
    }

    @Test
    fun `순위를 집계하지 못해도 기록은 보여주고 순위만 비운다`() = runTest {
        store.putRecord(entry("a", 90), circle)
        store.countFailure = true

        val detail = detail("a")

        assertNull(detail.rank)
        assertEquals(90, detail.score)
    }

    @Test
    fun `인증 전이면 인증 실패로 알린다`() = runTest {
        store.putRecord(entry("a", 90), circle)
        uid = null

        assertEquals(
            CircleRecordResult.Failure(CircleRankingFailure.AUTH),
            ranking.detail("a")
        )
    }

    @Test
    fun `순위표 줄은 기록 상세를 열 UID를 가진다`() = runTest {
        store.putRecord(entry("a", 90), circle)
        store.putRecord(entry("me", 80), circle)

        val board = (ranking.load() as CircleRankingResult.Success).board

        assertEquals(listOf("a", "me"), board.top.map { it.uid })
        assertEquals("me", board.mine?.uid)
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
