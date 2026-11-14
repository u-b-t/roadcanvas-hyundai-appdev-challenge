package kr.roadcanvas.app.challenge

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.ResultCell
import kr.roadcanvas.app.territory.ResultCellKind
import kr.roadcanvas.app.territory.TERRITORY_CELL_LAT_STEP
import kr.roadcanvas.app.territory.TerritoryCaptureState
import kr.roadcanvas.app.territory.TerritoryCell
import kr.roadcanvas.app.territory.TerritoryCellsResult
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritoryGrid
import kr.roadcanvas.app.territory.TerritorySyncUiState
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import kr.roadcanvas.app.territory.territoryCellsOf
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeResultTerritoryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val inProgress = FakeInProgressDrawingRepository()
    private val completed = FakeCompletedDrawingRepository()
    private val store = FakeTerritoryStore()
    private var captureCount = 0
    private val game = TerritoryGame(
        store,
        FakeTerritoryCaptureRepository(),
        FakeTerritoryOwnerProvider(),
        clock = { START },
        newCaptureId = { "capture-${++captureCount}" },
        conflictRetryDelayMillis = 0
    )
    private val recorder = ChallengeRecorder(FakeLocationProvider(), inProgress, clock = { START })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun finish(
        mode: DrawingMode,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        radius: Double = 50.0
    ): ChallengeResultViewModel {
        inProgress.start(mode, START)
        inProgress.appendPoints(
            TestPaths.circle(radius).mapIndexed { i, it ->
                RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
            }
        )
        return ChallengeResultViewModel(
            startedAtMillis = START,
            endedAtMillis = START + 60_000L,
            savedStateHandle = savedStateHandle,
            recorder = recorder,
            inProgressRepository = inProgress,
            completedRepository = completed,
            territoryGame = game
        )
    }

    private val ChallengeResultViewModel.capture: TerritoryCaptureState?
        get() = (territoryState.value as? TerritorySyncUiState.Capture)?.state

    @Test
    fun `차지한 땅따먹기를 저장하면 획득을 서버에 제출한다`() = runTest(dispatcher) {
        val viewModel = finish(DrawingMode.TERRITORY)

        val submitted = viewModel.capture as TerritoryCaptureState.Submitted
        assertTrue(submitted.outcome.newCells > 0)
        assertEquals(listOf("capture-1"), store.submitRequests)
    }

    @Test
    fun `결과 칸은 빼앗은 칸, 이미 내 칸, 새 칸과 주변 다른 사용자 칸으로 나눈다`() = runTest(dispatcher) {
        val boundary = (
            TerritoryCalculator().calculate(
                TestPaths.circle(200.0)
            ) as TerritoryResult.Claimed
            )
            .boundary
        val cells = (territoryCellsOf(boundary) as TerritoryCellsResult.Cells).cells
        val north = boundary.maxOf { it.latitude } +
            TERRITORY_CELL_LAT_STEP * 0.75
        val outside = TerritoryGrid.cellOf(
            GeoPoint(
                north,
                boundary.map {
                    it.longitude
                }.average()
            )
        ).id
        store.putCells("other", "드라이버-other", cells.take(2) + outside)
        store.putCells(FakeTerritoryOwnerProvider.DEFAULT_OWNER.uid, "나", cells.drop(2).take(1))

        val result = finish(DrawingMode.TERRITORY, radius = 200.0).resultCells.value!!

        assertEquals(
            cells.take(2).map { ResultCell(it, ResultCellKind.STOLEN, "드라이버-other") },
            result.captured.filter { it.kind == ResultCellKind.STOLEN }
        )
        assertEquals(
            listOf(cells[2]),
            result.captured.filter { it.kind == ResultCellKind.KEPT }.map { it.id }
        )
        assertEquals(cells.size - 3, result.captured.count { it.kind == ResultCellKind.CAPTURED })
        assertEquals(listOf(TerritoryCell(outside, "other", mine = false)), result.nearby)
    }

    @Test
    fun `서버에 연결하지 못하면 차지한 칸만 나누지 않고 그린다`() = runTest(dispatcher) {
        store.networkFailure = true

        val result = finish(DrawingMode.TERRITORY).resultCells.value!!

        assertTrue(result.captured.isNotEmpty())
        assertTrue(result.captured.all { it.kind == ResultCellKind.CAPTURED })
        assertTrue(result.nearby.isEmpty())
    }

    @Test
    fun `땅따먹기가 아니면 결과 칸이 없다`() = runTest(dispatcher) {
        assertNull(finish(DrawingMode.CIRCLE).resultCells.value)
    }

    @Test
    fun `결과 화면에 들어오면 이 기록을 먼저 제출하고 반영하지 못한 다른 획득도 다시 제출한다`() = runTest(dispatcher) {
        store.networkFailure = true
        val other = TerritoryCalculator().calculate(TestPaths.circle(60.0))
            as TerritoryResult.Claimed
        game.capture(99, other)
        store.networkFailure = false

        val viewModel = finish(DrawingMode.TERRITORY)

        assertTrue(viewModel.capture is TerritoryCaptureState.Submitted)
        assertEquals(listOf("capture-1", "capture-2", "capture-1"), store.submitRequests)
        assertTrue(store.capture("capture-1") != null)
    }

    @Test
    fun `연결하지 못하면 대기로 보이고 다시 제출하면 반영한다`() = runTest(dispatcher) {
        store.networkFailure = true
        val viewModel = finish(DrawingMode.TERRITORY)
        assertEquals(
            TerritoryFailure.NETWORK,
            (viewModel.capture as TerritoryCaptureState.Pending).failure
        )

        store.networkFailure = false
        viewModel.submitTerritory()

        assertTrue(viewModel.capture is TerritoryCaptureState.Submitted)
        assertEquals(listOf("capture-1", "capture-1", "capture-1"), store.submitRequests)
    }

    @Test
    fun `결과 화면을 다시 만들어도 같은 획득을 다시 만들지 않는다`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        finish(DrawingMode.TERRITORY, handle)
        val savedId = handle.get<Long>(ChallengeResultViewModel.KEY_SAVED_ID)!!

        val recreated = ChallengeResultViewModel(
            startedAtMillis = START,
            endedAtMillis = START + 60_000L,
            savedStateHandle = SavedStateHandle(
                mapOf(ChallengeResultViewModel.KEY_SAVED_ID to savedId)
            ),
            recorder = recorder,
            inProgressRepository = inProgress,
            completedRepository = completed,
            territoryGame = game
        )

        assertTrue(recreated.capture is TerritoryCaptureState.Submitted)
        assertEquals(listOf("capture-1"), store.submitRequests)
    }

    @Test
    fun `땅따먹기가 아닌 결과는 서버 반영 상태를 보이지 않는다`() = runTest(dispatcher) {
        assertNull(finish(DrawingMode.CIRCLE).territoryState.value)
        assertTrue(store.submitRequests.isEmpty())
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
