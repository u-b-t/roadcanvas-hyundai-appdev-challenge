package kr.roadcanvas.app.territory

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryGameTest {
    private val store = FakeTerritoryStore()
    private val captures = FakeTerritoryCaptureRepository()
    private val owner = FakeTerritoryOwnerProvider()
    private var now = 1_000_000L
    private var ids = 0
    private val game = TerritoryGame(
        store,
        captures,
        owner,
        clock = { now },
        newCaptureId = { "capture-${++ids}" },
        conflictRetryDelayMillis = 0
    )

    private val latSpan = Geohash.latitudeSpan(TERRITORY_CELL_PRECISION)
    private val lonSpan = Geohash.longitudeSpan(TERRITORY_CELL_PRECISION)
    private val base = Geohash.bounds("wydm9qy")

    /** 기준 칸에서 [rows] x [cols]칸을 덮는 차지한 영역 */
    private fun claimed(rows: Int, cols: Int) = TerritoryResult.Claimed(
        areaSquareMeters = rows * cols * TERRITORY_CELL_AREA_SQUARE_METERS,
        boundary = listOf(
            GeoPoint(base.south, base.west),
            GeoPoint(base.south, base.west + cols * lonSpan),
            GeoPoint(base.south + rows * latSpan, base.west + cols * lonSpan),
            GeoPoint(base.south + rows * latSpan, base.west)
        ),
        perimeterMeters = 0.0
    )

    private fun cellsOf(rows: Int, cols: Int) =
        (territoryCellsOf(claimed(rows, cols).boundary) as TerritoryCellsResult.Cells).cells

    private suspend fun capture(drawingId: Long = 1L, rows: Int = 2, cols: Int = 3) =
        (game.capture(drawingId, claimed(rows, cols)) as TerritoryCall.Success).value

    private suspend fun state(drawingId: Long = 1L) =
        (game.state(drawingId) as TerritoryCall.Success).value

    @Test
    fun `새 칸, 빼앗은 칸, 이미 내 칸을 나눠 반영하고 순위 칸 수를 바꾼다`() = runTest {
        val cells = cellsOf(2, 3)
        store.putCells("other", "다른 사람", cells.take(2))
        store.putCells("test", "드라이버-test", cells.drop(2).take(1))

        val result = capture()

        assertEquals(
            TerritoryCaptureState.Submitted(
                TerritoryCaptureOutcome(
                    newCells = 3,
                    stolenCells = 2,
                    ownCells = 1,
                    losses = listOf(TerritoryLoss("other", "다른 사람", cells.take(2))),
                    ownCellIds = listOf(cells[2])
                )
            ),
            result
        )
        assertEquals(6L, store.owner("test")?.cellCount)
        assertEquals(0L, store.owner("other")?.cellCount)
        assertTrue(store.cells.filterKeys { it in cells }.values.all { it == "test" })
        assertEquals(CaptureSyncStatus.SUBMITTED, captures.all.single().status)
        assertEquals(result, state())
    }

    @Test
    fun `이전 소유자별 빼앗은 칸은 이 실행에서만 남고 다시 만든 게임은 칸 수만 읽는다`() = runTest {
        val cells = cellsOf(2, 3)
        store.putCells("other", "다른 사람", cells.take(2))
        capture()

        val restarted = TerritoryGame(store, captures, owner, clock = { now })
        val reloaded = (restarted.state(1L) as TerritoryCall.Success).value

        assertEquals(1, (state() as TerritoryCaptureState.Submitted).outcome.losses.size)
        assertEquals(
            TerritoryCaptureState.Submitted(TerritoryCaptureOutcome(4, 2, 0)),
            reloaded
        )
    }

    @Test
    fun `같은 기록은 처음 만든 획득 ID를 다시 쓴다`() = runTest {
        capture()
        capture()

        assertEquals(listOf("capture-1"), store.submitRequests)
        assertEquals(1, captures.all.size)
    }

    @Test
    fun `너무 큰 영역은 제출하지 않고 기기에 획득을 만들지 않는다`() = runTest {
        val result = capture(rows = 20, cols = 21)

        assertEquals(TerritoryCaptureState.TooLarge(TERRITORY_MAX_CELLS_PER_CAPTURE), result)
        assertTrue(store.submitRequests.isEmpty())
        assertNull(state())
    }

    @Test
    fun `경합으로 거부되면 같은 획득 ID로 5번까지 실행한다`() = runTest {
        store.conflictsBeforeSuccess = 4

        val result = capture()

        assertTrue(result is TerritoryCaptureState.Submitted)
        assertEquals(List(5) { "capture-1" }, store.submitRequests)
    }

    @Test
    fun `5번 모두 경합이면 기기에 남기고 다음에 다시 제출한다`() = runTest {
        store.conflictsBeforeSuccess = 5

        assertEquals(
            TerritoryCaptureState.Pending(6, TerritoryFailure.CONFLICT),
            capture()
        )
        assertEquals(5, store.submitRequests.size)
        assertEquals(CaptureSyncStatus.PENDING, captures.all.single().status)

        assertEquals(TerritoryCall.Success(1), game.resubmitPending())
        assertTrue(state() is TerritoryCaptureState.Submitted)
    }

    @Test
    fun `연결 실패는 바로 다시 실행하지 않고 기기에 남긴다`() = runTest {
        store.networkFailure = true

        assertEquals(TerritoryCaptureState.Pending(6, TerritoryFailure.NETWORK), capture())
        assertEquals(1, store.submitRequests.size)
        assertEquals(TerritoryCaptureState.Pending(6, null), state())

        store.networkFailure = false
        assertEquals(TerritoryCall.Success(1), game.resubmitPending())
        assertEquals(6L, store.owner("test")?.cellCount)
        assertEquals(TerritoryCall.Success(0), game.resubmitPending())
    }

    @Test
    fun `응답을 잃은 획득은 다시 제출해도 한 번만 반영한다`() = runTest {
        store.loseNextSubmitResponse = true

        assertEquals(TerritoryCaptureState.Pending(6, TerritoryFailure.NETWORK), capture())
        game.resubmitPending()

        assertEquals(
            TerritoryCaptureState.Submitted(TerritoryCaptureOutcome(6, 0, 0)),
            state()
        )
        assertEquals(6L, store.owner("test")?.cellCount)
    }

    @Test
    fun `24시간이 지난 미제출 획득은 반영을 포기한다`() = runTest {
        store.networkFailure = true
        capture()
        store.networkFailure = false
        now += TERRITORY_SUBMIT_DEADLINE_MILLIS

        assertEquals(TerritoryCaptureState.Expired(6), state())
        assertEquals(TerritoryCall.Success(0), game.resubmitPending())
        assertEquals(CaptureSyncStatus.EXPIRED, captures.all.single().status)
        assertEquals(1, store.submitRequests.size)
        assertEquals(TerritoryCaptureState.Expired(6), capture())
    }

    @Test
    fun `인증하지 못하면 기기에 남긴다`() = runTest {
        owner.owner = null

        assertEquals(TerritoryCaptureState.Pending(6, TerritoryFailure.AUTH), capture())
        assertTrue(store.submitRequests.isEmpty())
    }

    @Test
    fun `기기에 저장하지 못하면 제출하지 않는다`() = runTest {
        captures.failure = IOException("disk")

        assertEquals(
            TerritoryCall.Failure(TerritoryFailure.STORAGE),
            game.capture(1L, claimed(2, 3))
        )
        assertTrue(store.submitRequests.isEmpty())
    }

    @Test
    fun `지도 범위의 칸을 읽고 내 칸을 구분한다`() = runTest {
        val cells = cellsOf(1, 2)
        store.putCells("test", "드라이버-test", cells.take(1))
        store.putCells("other", "다른 사람", cells.drop(1))
        val view = GeoBounds(base.south, base.west, base.north, base.east + lonSpan)

        val result = (game.mapCells(view) as TerritoryCall.Success).value

        assertEquals(
            TerritoryMapCells.Loaded(
                listOf(
                    TerritoryCell(cells[0], "test", mine = true),
                    TerritoryCell(cells[1], "other", mine = false)
                )
            ),
            result
        )
    }

    @Test
    fun `칸이 300개를 넘거나 범위가 넓으면 확대하라고 한다`() = runTest {
        store.putCells("other", "다른 사람", cellsOf(16, 19))
        val view = GeoBounds(
            base.south,
            base.west,
            base.north + 16 * latSpan,
            base.east + 19 * lonSpan
        )

        assertEquals(
            TerritoryCall.Success(TerritoryMapCells.TooMany(TERRITORY_MAP_MAX_CELLS)),
            game.mapCells(view)
        )
        val wide = GeoBounds(37.0, 126.0, 38.0, 127.0)
        val requests = store.requests.size
        assertEquals(
            TerritoryCall.Success(TerritoryMapCells.TooMany(TERRITORY_MAP_MAX_CELLS)),
            game.mapCells(wide)
        )
        assertEquals(requests, store.requests.size)
    }

    @Test
    fun `내 칸 수와 상위 10명 순위를 보유 칸 수 내림차순으로 준다`() = runTest {
        val cells = cellsOf(10, 12)
        (0 until 11).forEach { index ->
            store.putCells("u$index", "사용자$index", cells.drop(index * 10).take(index + 1))
        }
        store.putCells("test", "드라이버-test", cells.takeLast(5))

        assertEquals(TerritoryCall.Success(5L), game.myCellCount())
        val ranking = (game.ranking() as TerritoryCall.Success).value
        assertEquals(TERRITORY_RANKING_SIZE, ranking.size)
        assertEquals((1..10).toList(), ranking.map { it.rank })
        assertEquals(ranking.sortedByDescending { it.cellCount }, ranking)
        assertEquals("u10", ranking.first().uid)
        assertEquals(11L, ranking.first().cellCount)
        assertTrue(ranking.any { it.mine && it.uid == "test" })
    }

    @Test
    fun `서버에 연결하지 못하면 조회 실패를 알린다`() = runTest {
        store.networkFailure = true

        assertEquals(TerritoryCall.Failure(TerritoryFailure.NETWORK), game.myCellCount())
        assertEquals(TerritoryCall.Failure(TerritoryFailure.NETWORK), game.ranking())
    }

    @Test
    fun `저장 실패도 상태 조회 실패로 알린다`() = runTest {
        captures.failure = IOException("disk")

        assertEquals(TerritoryCall.Failure(TerritoryFailure.STORAGE), game.state(1L))
        assertEquals(TerritoryCall.Failure(TerritoryFailure.STORAGE), game.resubmitPending())
        assertTrue((captures.findByDrawing(1L)) is StorageResult.Failure)
    }
}
