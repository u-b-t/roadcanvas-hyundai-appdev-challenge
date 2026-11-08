package kr.roadcanvas.app.challenge

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingRequest
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.score.ZeroScoreReason
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeResultViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val inProgress = FakeInProgressDrawingRepository()
    private val completed = FakeCompletedDrawingRepository()
    private val locationProvider = FakeLocationProvider()
    private var now = START
    private val recorder = ChallengeRecorder(locationProvider, inProgress, clock = { now })
    private val storageFailures = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        repository: CompletedDrawingRepository = completed
    ) = ChallengeResultViewModel(
        startedAtMillis = START,
        endedAtMillis = END,
        savedStateHandle = handle,
        recorder = recorder,
        inProgressRepository = inProgress,
        completedRepository = repository,
        onStorageFailure = { storageFailures += it }
    )

    private suspend fun saveInProgress(path: List<RoutePoint>, startedAtMillis: Long = START) {
        inProgress.start(DrawingMode.CIRCLE, startedAtMillis)
        inProgress.appendPoints(path)
    }

    private fun circlePath() = TestPaths.circle(50.0).mapIndexed { i, it ->
        RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
    }

    private fun savedSummaries() =
        (runBlocking { completed.summaries() } as StorageResult.Success).value

    private fun inProgressDrawing() =
        (runBlocking { inProgress.load() } as StorageResult.Success).value

    @Test
    fun `종료한 기록으로 점수를 계산하고 완료 기록을 저장한 뒤 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(circlePath())

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertTrue(state.result.score.score >= 90)
        assertEquals(END - START, state.result.durationMillis)
        val summary = savedSummaries().single()
        assertEquals(state.result.score.score, summary.score)
        assertEquals(END, summary.completedAtMillis)
        assertEquals(END - START, summary.durationMillis)
        assertNull(inProgressDrawing())
    }

    @Test
    fun `저장하는 동안에는 저장 중 상태다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val gate = CompletableDeferred<Unit>()
        val gated = object : CompletedDrawingRepository by completed {
            override suspend fun save(drawing: CompletedDrawing): StorageResult<Long> {
                gate.await()
                return completed.save(drawing)
            }
        }

        val viewModel = viewModel(repository = gated)

        val saving = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(ResultSaveStatus.SAVING, saving.saveStatus)
        assertTrue(inProgressDrawing() != null)

        gate.complete(Unit)

        val saved = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(ResultSaveStatus.SAVED, saved.saveStatus)
    }

    @Test
    fun `같은 프로세스에서 종료했으면 메모리의 경로와 종료 시각을 쓴다`() = runTest(dispatcher) {
        backgroundScope.launch { recorder.run(RecordingRequest.New(DrawingMode.CIRCLE)) }
        locationProvider.subscriberCount.first { it > 0 }
        circlePath().forEach {
            locationProvider.sendLocation(LocationSample(it.latitude, it.longitude, it.timeMillis))
        }
        // 마지막 점 저장에 실패해 메모리 경로에만 남은 상황
        inProgress.failure = IllegalStateException("저장 실패")
        locationProvider.sendLocation(LocationSample(37.0, 127.0, END))
        inProgress.failure = null
        now = END
        recorder.finish()

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(circlePath().size + 1, state.result.path.size)
        assertEquals(END - START, state.result.durationMillis)
        assertNull(inProgressDrawing())
    }

    @Test
    fun `이동하지 않은 0점 도전도 사유와 함께 저장한다`() = runTest(dispatcher) {
        saveInProgress(listOf(RoutePoint(37.5665, 126.978, START)))

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(0, state.result.score.score)
        assertEquals(ZeroScoreReason.INSUFFICIENT_POINTS, state.result.score.zeroReason)
        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(listOf(0), savedSummaries().map { it.score })
    }

    @Test
    fun `저장에 실패하면 결과를 유지하고 진행 중 기록을 남긴다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        completed.failure = IllegalStateException("저장 실패")

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.FAILED, state.saveStatus)
        assertTrue(state.result.score.score >= 90)
        assertEquals(circlePath().size, inProgressDrawing()?.path?.size)
        assertEquals(1, storageFailures.size)
    }

    @Test
    fun `다시 저장하면 한 번만 저장하고 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        completed.failure = IllegalStateException("저장 실패")
        val viewModel = viewModel()
        completed.failure = null

        viewModel.retrySave()
        viewModel.retrySave()

        val state = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(1, savedSummaries().size)
        assertNull(inProgressDrawing())
    }

    @Test
    fun `저장한 뒤 화면이 다시 만들어지면 다시 저장하지 않고 저장한 결과를 보여준다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val handle = SavedStateHandle()
        val first = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        val recreated = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, recreated.saveStatus)
        assertEquals(first.result.score, recreated.result.score)
        assertEquals(first.result.path, recreated.result.path)
        assertEquals(1, savedSummaries().size)
    }

    @Test
    fun `저장한 뒤 진행 중 기록을 지우지 못했으면 다시 만들어질 때 지운다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val handle = SavedStateHandle()
        val failingClear = object : CompletedDrawingRepository by completed {
            override suspend fun save(drawing: CompletedDrawing): StorageResult<Long> =
                completed.save(drawing).also {
                    inProgress.failure = IllegalStateException("삭제 실패")
                }
        }
        val first = viewModel(
            handle,
            failingClear
        ).uiState.value as ChallengeResultUiState.Calculated
        assertEquals(ResultSaveStatus.SAVED, first.saveStatus)
        inProgress.failure = null
        assertTrue(inProgressDrawing() != null)

        viewModel(handle)

        assertNull(inProgressDrawing())
        assertEquals(1, savedSummaries().size)
    }

    @Test
    fun `완료 기록을 저장한 뒤 진행 중 기록을 지우기 전에 종료됐으면 다시 저장하지 않고 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        inProgress.finish(END)
        val result = challengeResultOf(DrawingMode.CIRCLE, circlePath(), START, END)
        val savedId = (completed.save(result.toCompletedDrawing()) as StorageResult.Success).value
        val handle = SavedStateHandle()

        val state = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(1, savedSummaries().size)
        assertEquals(savedId, handle.get<Long>(ChallengeResultViewModel.KEY_SAVED_ID))
        assertNull(inProgressDrawing())
    }

    @Test
    fun `다른 도전의 진행 중 기록은 결과로 쓰지 않고 지우지 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), startedAtMillis = START + 1)

        val state = viewModel().uiState.value

        assertEquals(ChallengeResultUiState.Unavailable, state)
        assertTrue(inProgressDrawing() != null)
        assertTrue(savedSummaries().isEmpty())
    }

    @Test
    fun `진행 중 기록을 불러오지 못하면 결과를 표시할 수 없다`() = runTest(dispatcher) {
        inProgress.failure = IllegalStateException("불러오기 실패")

        assertEquals(ChallengeResultUiState.Unavailable, viewModel().uiState.value)
        assertEquals(1, storageFailures.size)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val END = START + 185_000L
    }
}
