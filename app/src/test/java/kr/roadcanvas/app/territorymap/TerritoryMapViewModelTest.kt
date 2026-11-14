package kr.roadcanvas.app.territorymap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.TERRITORY_AREA_METRIC
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.map.MapBounds
import kr.roadcanvas.app.map.RoutePoint as MapPoint
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.TERRITORY_CELL_LAT_STEP
import kr.roadcanvas.app.territory.TERRITORY_MAP_MAX_CELLS
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritoryGrid
import kr.roadcanvas.app.territory.TerritoryMapCells
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private val location = FakeLocationProvider()

    /** 현재 위치를 기다리지 않는 화면. 위치를 기다리는 시험은 [waitMillis]를 준다. */
    private fun newViewModel(initialCenter: MapPoint? = null, waitMillis: Long = 0) =
        TerritoryMapViewModel(game, drawings, location, initialCenter, waitMillis)

    private suspend fun saveTerritory(
        path: List<RoutePoint>,
        completedAtMillis: Long,
        area: Double = 10_000.0
    ) {
        drawings.save(
            CompletedDrawing(
                mode = DrawingMode.TERRITORY,
                completedAtMillis = completedAtMillis,
                path = path,
                score = 0,
                metrics = mapOf(TERRITORY_AREA_METRIC to area),
                distanceMeters = 100.0,
                durationMillis = 60_000
            )
        )
    }

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
        val step = TERRITORY_CELL_LAT_STEP
        return (0 until 20).flatMap { row ->
            (0 until 20).map { col ->
                TerritoryGrid.cellOf(GeoPoint(37.560 + row * step, 126.972 + col * step)).id
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

        val state = newViewModel().state.value

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
    fun `위치를 받지 못하면 넘겨받은 위치, 최근 기록의 마지막 위치, 서울시청 순으로 연다`() = runTest(dispatcher) {
        val given = MapPoint(37.5, 127.0)
        assertEquals(given, newViewModel(given).state.value.center)
        assertEquals(DEFAULT_TERRITORY_MAP_CENTER, newViewModel().state.value.center)

        saveTerritory(
            listOf(RoutePoint(37.40, 127.10, 0L), RoutePoint(37.42, 127.12, 1_000L)),
            completedAtMillis = 60_000
        )
        assertEquals(MapPoint(37.42, 127.12), newViewModel().state.value.center)
    }

    @Test
    fun `현재 위치를 기다렸다가 그 위치에서 열고 내 위치로 보여준다`() = runTest(dispatcher) {
        val viewModel = newViewModel(waitMillis = 2_000)
        backgroundScope.launch { viewModel.myLocation.collect {} }
        assertNull(viewModel.state.value.center)

        location.sendLocation(LocationSample(37.6, 127.05, 0L))

        assertEquals(MapPoint(37.6, 127.05), viewModel.state.value.center)
        assertEquals(MapPoint(37.6, 127.05), viewModel.myLocation.value)
    }

    @Test
    fun `기다려도 위치가 오지 않으면 기본 위치에서 연다`() = runTest(dispatcher) {
        val viewModel = newViewModel(waitMillis = 2_000)
        assertNull(viewModel.state.value.center)

        advanceTimeBy(2_001)

        assertEquals(DEFAULT_TERRITORY_MAP_CENTER, viewModel.state.value.center)
    }

    @Test
    fun `내 땅으로는 차지한 기록이 가장 많이 모인 곳으로 지도를 옮긴다`() = runTest(dispatcher) {
        // 강남 쪽 두 건과 더 최근의 시청 한 건. 0점(면적 없음) 기록은 세지 않는다.
        saveTerritory(listOf(RoutePoint(37.497, 127.027, 0L)), completedAtMillis = 1_000)
        saveTerritory(listOf(RoutePoint(37.499, 127.029, 0L)), completedAtMillis = 2_000)
        saveTerritory(listOf(RoutePoint(37.5665, 126.978, 0L)), completedAtMillis = 3_000)
        saveTerritory(listOf(RoutePoint(37.5665, 126.978, 0L)), 4_000, area = 0.0)
        saveTerritory(listOf(RoutePoint(37.5665, 126.978, 0L)), 5_000, area = 0.0)
        val viewModel = newViewModel()
        val opened = viewModel.state.value
        assertEquals(MapPoint(37.5665, 126.978), opened.center)

        viewModel.moveToMyLand()
        val moved = viewModel.state.value
        assertEquals(37.498, moved.center!!.latitude, 1e-9)
        assertEquals(127.028, moved.center!!.longitude, 1e-9)
        assertNotEquals(opened.cameraMoveId, moved.cameraMoveId)

        // 같은 곳으로 다시 옮겨도 카메라를 다시 옮기도록 이동 번호가 바뀐다.
        viewModel.moveToMyLand()
        assertNotEquals(moved.cameraMoveId, viewModel.state.value.cameraMoveId)
    }

    @Test
    fun `차지한 기록이 없으면 내 땅으로 옮기지 않는다`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        val before = viewModel.state.value
        assertNull(before.myLandCenter)

        viewModel.moveToMyLand()

        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun `지도 이동이 끝나면 그 범위의 칸을 내 칸과 다른 사용자 칸으로 읽는다`() = runTest(dispatcher) {
        store.putCells(myUid, "드라이버-test", cellsNearCityHall(3))
        store.putCells("other", "드라이버-other", cellsNearCityHall(2, skip = 3))
        val viewModel = newViewModel()
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
        val viewModel = newViewModel()

        viewModel.onVisibleBoundsChange(bounds)

        assertTrue(viewModel.state.value.tooManyCells)
    }

    @Test
    fun `차지한 칸이 없으면 빈 영토다`() = runTest(dispatcher) {
        val state = newViewModel().state.value

        assertTrue(state.emptyTerritory)
        assertEquals(emptyList<Any>(), (state.ranking as TerritoryLoad.Loaded).value)
    }

    @Test
    fun `연결에 실패하면 이유를 알리고 새로고침하면 지도 칸까지 다시 읽는다`() = runTest(dispatcher) {
        store.putCells(myUid, "드라이버-test", cellsNearCityHall(2))
        store.networkFailure = true
        val viewModel = newViewModel()
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

        val state = newViewModel().state.value

        assertEquals(TerritoryFailure.AUTH, state.failure)
    }
}
