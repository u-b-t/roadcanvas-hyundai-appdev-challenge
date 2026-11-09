package kr.roadcanvas.app.territorymap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.map.MapBounds
import kr.roadcanvas.app.map.RoutePoint as MapPoint
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.Geohash
import kr.roadcanvas.app.territory.TERRITORY_CELL_PRECISION
import kr.roadcanvas.app.territory.TERRITORY_MAP_MAX_CELLS
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritoryMapCells
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryMapViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakeTerritoryStore()
    private val owner = FakeTerritoryOwnerProvider()
    private val drawings = FakeCompletedDrawingRepository()
    private var captureCount = 0
    private val game = TerritoryGame(
        store,
        FakeTerritoryCaptureRepository(),
        owner,
        clock = { 1_000_000L },
        newCaptureId = { "capture-${++captureCount}" },
        conflictRetryDelayMillis = 0
    )
    private val myUid = FakeTerritoryOwnerProvider.DEFAULT_OWNER.uid

    /** 서울시청 주변 화면 범위 */
    private val bounds = MapBounds(37.555, 126.965, 37.595, 127.005)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun cellsNearCityHall(count: Int, skip: Int = 0): List<String> {
        val step = Geohash.latitudeSpan(TERRITORY_CELL_PRECISION)
        return (0 until 20).flatMap { row ->
            (0 until 20).map { col ->
                Geohash.encode(
                    GeoPoint(37.560 + row * step, 126.972 + col * step),
                    TERRITORY_CELL_PRECISION
                )
            }
        }.distinct().drop(skip).take(count)
    }

    @Test
    fun `들어오면 반영하지 못한 획득을 다시 제출한 뒤 내 칸 수와 순위를 읽는다`() = runTest(dispatcher) {
        store.putCells("other", "드라이버-other", cellsNearCityHall(5))
        store.networkFailure = true
        val claimed = TerritoryCalculator().calculate(TestPaths.circle(50.0))
            as TerritoryResult.Claimed
        game.capture(1, claimed)
        store.networkFailure = false

        val state = TerritoryMapViewModel(game, drawings).state.value

        assertEquals(listOf("capture-1", "capture-1"), store.submitRequests)
        val myCount = (state.myCellCount as TerritoryLoad.Loaded).value
        assertTrue(myCount > 0)
        val ranking = (state.ranking as TerritoryLoad.Loaded).value
        assertEquals(listOf("other", myUid), ranking.map { it.uid })
        assertEquals(listOf(false, true), ranking.map { it.mine })
        assertEquals(listOf(1, 2), ranking.map { it.rank })
        assertNull(state.failure)
        assertFalse(state.emptyTerritory)
    }

    @Test
    fun `처음 열 위치는 넘겨받은 위치, 최근 땅따먹기 기록, 서울시청 순으로 정한다`() = runTest(dispatcher) {
        val given = MapPoint(37.5, 127.0)
        assertEquals(given, TerritoryMapViewModel(game, drawings, given).state.value.center)
        assertEquals(
            DEFAULT_TERRITORY_MAP_CENTER,
            TerritoryMapViewModel(game, drawings).state.value.center
        )

        val path = listOf(RoutePoint(37.40, 127.10, 0L), RoutePoint(37.42, 127.12, 1_000L))
        drawings.save(
            CompletedDrawing(
                mode = DrawingMode.TERRITORY,
                completedAtMillis = 60_000,
                path = path,
                score = 0,
                metrics = emptyMap(),
                distanceMeters = 100.0,
                durationMillis = 60_000
            )
        )
        val center = TerritoryMapViewModel(game, drawings).state.value.center!!
        assertEquals(37.41, center.latitude, 1e-9)
        assertEquals(127.11, center.longitude, 1e-9)
    }

    @Test
    fun `지도 이동이 끝나면 그 범위의 칸을 내 칸과 다른 사용자 칸으로 읽는다`() = runTest(dispatcher) {
        store.putCells(myUid, "드라이버-test", cellsNearCityHall(3))
        store.putCells("other", "드라이버-other", cellsNearCityHall(2, skip = 3))
        val viewModel = TerritoryMapViewModel(game, drawings)
        assertEquals(TerritoryLoad.Loading, viewModel.state.value.cells)

        viewModel.onVisibleBoundsChange(bounds)

        val cells = (
            (viewModel.state.value.cells as TerritoryLoad.Loaded).value
                as TerritoryMapCells.Loaded
            ).cells
        assertEquals(3, cells.count { it.mine })
        assertEquals(2, cells.count { !it.mine })
        assertEquals(3L, (viewModel.state.value.myCellCount as TerritoryLoad.Loaded).value)
    }

    @Test
    fun `읽을 칸이 300개를 넘으면 확대하라고 안내한다`() = runTest(dispatcher) {
        store.putCells("other", "드라이버-other", cellsNearCityHall(TERRITORY_MAP_MAX_CELLS + 1))
        val viewModel = TerritoryMapViewModel(game, drawings)

        viewModel.onVisibleBoundsChange(bounds)

        assertTrue(viewModel.state.value.tooManyCells)
    }

    @Test
    fun `차지한 칸이 없으면 빈 영토다`() = runTest(dispatcher) {
        val state = TerritoryMapViewModel(game, drawings).state.value

        assertTrue(state.emptyTerritory)
        assertEquals(emptyList<Any>(), (state.ranking as TerritoryLoad.Loaded).value)
    }

    @Test
    fun `연결에 실패하면 이유를 알리고 새로고침하면 지도 칸까지 다시 읽는다`() = runTest(dispatcher) {
        store.putCells(myUid, "드라이버-test", cellsNearCityHall(2))
        store.networkFailure = true
        val viewModel = TerritoryMapViewModel(game, drawings)
        viewModel.onVisibleBoundsChange(bounds)
        assertEquals(TerritoryFailure.NETWORK, viewModel.state.value.failure)
        assertFalse(viewModel.state.value.emptyTerritory)

        store.networkFailure = false
        viewModel.refresh()

        val state = viewModel.state.value
        assertNull(state.failure)
        assertEquals(2L, (state.myCellCount as TerritoryLoad.Loaded).value)
        assertTrue((state.cells as TerritoryLoad.Loaded).value is TerritoryMapCells.Loaded)
    }

    @Test
    fun `익명 인증에 실패하면 인증 실패를 알린다`() = runTest(dispatcher) {
        owner.owner = null

        val state = TerritoryMapViewModel(game, drawings).state.value

        assertEquals(TerritoryFailure.AUTH, state.failure)
    }
}
