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
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.TerritoryCaptureState
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritorySyncUiState
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
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
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): ChallengeResultViewModel {
        inProgress.start(mode, START)
        inProgress.appendPoints(
            TestPaths.circle(50.0).mapIndexed { i, it ->
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
