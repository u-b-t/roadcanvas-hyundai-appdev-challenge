package kr.roadcanvas.app.test

import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.cos
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.territory.CaptureSyncStatus
import kr.roadcanvas.app.territory.GeoBounds
import kr.roadcanvas.app.territory.StoredTerritoryCapture
import kr.roadcanvas.app.territory.TERRITORY_CELL_LAT_STEP
import kr.roadcanvas.app.territory.TERRITORY_CELL_LON_STEP
import kr.roadcanvas.app.territory.TERRITORY_MAP_MAX_CELLS
import kr.roadcanvas.app.territory.TERRITORY_MAX_CELLS_PER_CAPTURE
import kr.roadcanvas.app.territory.TERRITORY_RANKING_SIZE
import kr.roadcanvas.app.territory.TERRITORY_SUBMIT_DEADLINE_MILLIS
import kr.roadcanvas.app.territory.TerritoryCall
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import kr.roadcanvas.app.territory.TerritoryCaptureState
import kr.roadcanvas.app.territory.TerritoryCellsResult
import kr.roadcanvas.app.territory.TerritoryGrid
import kr.roadcanvas.app.territory.TerritoryMapCells
import kr.roadcanvas.app.territory.TerritoryRank
import kr.roadcanvas.app.territory.territoryCellsOf
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val DRAWING_ID = 1L
private const val ME = "test"
private const val OTHER = "other"
private const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180

/** 화면이 없으므로 서버 땅따먹기를 대체 서버 저장소 위에서 직접 호출해 규칙을 확인한다. */
class TerritoryCaptureSyncSteps {
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val game get() = fakeContainer.territoryGame
    private val store get() = fakeContainer.territoryStore

    private val latSpan = TERRITORY_CELL_LAT_STEP
    private val lonSpan = TERRITORY_CELL_LON_STEP

    /** 서울시청이 속한 칸의 서남쪽 모서리 */
    private val base = TerritoryGrid.bounds("118117_225719")
    private var lastState: TerritoryCaptureState? = null
    private var lastRows = 0
    private var lastCols = 0
    private var mapCells: TerritoryMapCells? = null
    private var ranking = emptyList<TerritoryRank>()

    @Given("다른 사용자가 {int}x{int}칸 영역 중 {int}칸을 소유하고 있다")
    fun otherOwnsSome(rows: Int, cols: Int, count: Int) {
        store.putCells(OTHER, "다른 사용자", cells(rows, cols).take(count))
    }

    @Given("내가 그 영역 중 {int}칸을 이미 소유하고 있다")
    fun iOwnSome(count: Int) {
        val free = cells(3, 4).filter { it !in store.cells }
        store.putCells(ME, "드라이버-test", free.take(count))
    }

    @Given("내가 1x{int}칸 영역 중 {int}칸을 소유하고 다른 사용자가 {int}칸을 소유하고 있다")
    fun mixedRow(cols: Int, mine: Int, others: Int) {
        lastRows = 1
        lastCols = cols
        val row = cells(1, cols)
        store.putCells(ME, "드라이버-test", row.take(mine))
        store.putCells(OTHER, "다른 사용자", row.drop(mine).take(others))
    }

    @Given("다른 사용자가 {int}x{int}칸 영역을 소유하고 있다")
    fun otherOwnsArea(rows: Int, cols: Int) {
        lastRows = rows
        lastCols = cols
        store.putCells(OTHER, "다른 사용자", cells(rows, cols))
    }

    @Given("사용자 {int}명이 각각 1칸부터 {int}칸까지 소유하고 있다")
    fun manyOwners(users: Int, max: Int) {
        val all = cells(12, 7)
        var offset = 0
        (1..users).forEach { index ->
            val count = index * max / users
            store.putCells("u$index", "사용자$index", all.drop(offset).take(count))
            offset += count
        }
    }

    @Given("격자를 바꾸기 전 geohash 칸으로 만든 획득이 기기에 남아 있다")
    fun legacyCapture() {
        runBlocking {
            fakeContainer.territoryCaptureRepository.save(
                StoredTerritoryCapture(
                    captureId = "legacy",
                    drawingId = DRAWING_ID,
                    cells = listOf("wydm9qy", "wydm9qz"),
                    createdAtMillis = fakeContainer.territoryNowMillis,
                    status = CaptureSyncStatus.PENDING
                )
            )
        }
    }

    @Given("서버가 다음 제출 {int}번을 동시 획득과 겹쳐 거부한다")
    fun conflicts(count: Int) {
        store.conflictsBeforeSuccess = count
    }

    @Given("서버가 다음 제출을 반영한 뒤 응답을 잃는다")
    fun loseResponse() {
        store.loseNextSubmitResponse = true
    }

    @Given("땅따먹기 서버에 연결할 수 없다")
    fun offline() {
        store.networkFailure = true
    }

    @Given("{int}x{int}칸 영역을 차지한 기록을 서버에 제출했다")
    @When("{int}x{int}칸 영역을 차지한 기록을 서버에 제출한다")
    fun submit(rows: Int, cols: Int) {
        val result = runBlocking { game.capture(DRAWING_ID, claimed(rows, cols)) }
        lastState = (result as TerritoryCall.Success).value
    }

    @When("서버에 다시 연결되어 남은 획득을 다시 제출한다")
    fun reconnectAndResubmit() {
        store.networkFailure = false
        runBlocking { game.resubmitPending() }
        lastState = currentState()
    }

    @When("{int}시간이 지난 뒤 서버에 다시 연결되어 남은 획득을 다시 제출한다")
    fun laterReconnect(hours: Int) {
        fakeContainer.territoryNowMillis += hours * TERRITORY_SUBMIT_DEADLINE_MILLIS / 24
        reconnectAndResubmit()
    }

    @When("그 영역을 덮는 지도 범위의 칸을 조회한다")
    fun queryMap() {
        val view = GeoBounds(
            base.south + 0.1 * latSpan,
            base.west + 0.1 * lonSpan,
            base.south + (lastRows - 0.1) * latSpan,
            base.west + (lastCols - 0.1) * lonSpan
        )
        mapCells = (runBlocking { game.mapCells(view) } as TerritoryCall.Success).value
    }

    @When("땅따먹기 순위를 조회한다")
    fun queryRanking() {
        ranking = (runBlocking { game.ranking() } as TerritoryCall.Success).value
    }

    @Then("새로 차지한 칸 {int}개, 빼앗은 칸 {int}개, 이미 내 칸 {int}개로 반영된다")
    fun assertOutcome(new: Int, stolen: Int, own: Int) {
        val outcome = (lastState as TerritoryCaptureState.Submitted).outcome
        assertEquals(TerritoryCaptureOutcome(new, stolen, own), outcome.countsOnly())
        assertEquals(lastState, currentState())
    }

    @Then("내 보유 칸은 {int}개이고 다른 사용자의 보유 칸은 {int}개다")
    fun assertCounts(mine: Int, other: Int) {
        assertEquals(TerritoryCall.Success(mine.toLong()), runBlocking { game.myCellCount() })
        assertEquals(other.toLong(), store.owner(OTHER)?.cellCount ?: 0L)
    }

    @Then("영역이 너무 커서 획득하지 않는다")
    fun assertTooLarge() {
        assertEquals(TerritoryCaptureState.TooLarge(TERRITORY_MAX_CELLS_PER_CAPTURE), lastState)
    }

    @Then("서버에 제출한 획득이 없다")
    fun assertNoSubmit() {
        assertTrue(store.submitRequests.isEmpty())
        assertTrue(fakeContainer.territoryCaptureRepository.all.isEmpty())
    }

    @Then("이 기록은 아직 획득을 만들지 않은 것으로 보인다")
    fun assertNotCaptured() {
        assertEquals(TerritoryCall.Success(null), runBlocking { game.state(DRAWING_ID) })
    }

    @Then("서버에 제출 요청을 보내지 않았다")
    fun assertNoSubmitRequest() {
        assertTrue(store.submitRequests.isEmpty())
    }

    @Then("제출한 칸 {int}개는 모두 가로와 세로가 {int}m에서 {int}m 안쪽인 격자 칸이다")
    fun assertSquareCells(count: Int, side: Int, tolerance: Int) {
        val cells = checkNotNull(store.capture("capture-1")).cells
        assertEquals(count, cells.size)
        cells.forEach { id ->
            val bounds = TerritoryGrid.bounds(id)
            val height = (bounds.north - bounds.south) * METERS_PER_DEGREE
            val width = (bounds.east - bounds.west) * METERS_PER_DEGREE *
                cos(Math.toRadians(bounds.center.latitude))
            assertEquals("$id 세로", side.toDouble(), height, tolerance.toDouble())
            assertEquals("$id 가로", side.toDouble(), width, tolerance.toDouble())
        }
    }

    @Then("같은 획득 ID로 {int}번 제출했다")
    fun assertSubmitCount(count: Int) {
        assertEquals(count, store.submitRequests.size)
        assertEquals(1, store.submitRequests.toSet().size)
    }

    @Then("획득은 서버에 반영되지 않고 기기에 남는다")
    fun assertPending() {
        assertTrue("상태: $lastState", lastState is TerritoryCaptureState.Pending)
        assertTrue(currentState() is TerritoryCaptureState.Pending)
        assertTrue(store.capture("capture-1") == null)
    }

    @Then("획득은 서버에 반영하지 못함으로 표시된다")
    fun assertExpired() {
        assertTrue("상태: $lastState", lastState is TerritoryCaptureState.Expired)
        assertTrue(store.capture("capture-1") == null)
    }

    @Then("내 칸 {int}개와 다른 사용자 칸 {int}개를 받는다")
    fun assertMapCells(mine: Int, others: Int) {
        val cells = (mapCells as TerritoryMapCells.Loaded).cells
        assertEquals(mine, cells.count { it.mine })
        assertEquals(others, cells.count { !it.mine })
    }

    @Then("칸이 너무 많아 확대해야 한다")
    fun assertTooMany() {
        assertEquals(TerritoryMapCells.TooMany(TERRITORY_MAP_MAX_CELLS), mapCells)
    }

    @Then("보유 칸 {int}개인 사용자부터 내림차순으로 {int}명을 받는다")
    fun assertRanking(top: Int, size: Int) {
        assertEquals(TERRITORY_RANKING_SIZE, size)
        assertEquals(size, ranking.size)
        assertEquals(top.toLong(), ranking.first().cellCount)
        assertEquals(ranking.sortedByDescending { it.cellCount }, ranking)
        assertEquals((1..size).toList(), ranking.map { it.rank })
    }

    private fun currentState() =
        (runBlocking { game.state(DRAWING_ID) } as TerritoryCall.Success).value

    /** 기준 칸에서 북쪽으로 [rows]칸, 동쪽으로 [cols]칸을 덮는 차지한 영역 */
    private fun claimed(rows: Int, cols: Int) = TerritoryResult.Claimed(
        areaSquareMeters = 0.0,
        boundary = listOf(
            GeoPoint(base.south, base.west),
            GeoPoint(base.south, base.west + cols * lonSpan),
            GeoPoint(base.south + rows * latSpan, base.west + cols * lonSpan),
            GeoPoint(base.south + rows * latSpan, base.west)
        ),
        perimeterMeters = 0.0
    )

    private fun cells(rows: Int, cols: Int) =
        (territoryCellsOf(claimed(rows, cols).boundary) as TerritoryCellsResult.Cells).cells
}
