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
import kr.roadcanvas.app.score.TerritoryResult
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

    private fun suggestingViewModel(suggestion: String?) = ChallengeResultViewModel(
        startedAtMillis = START,
        endedAtMillis = END,
        savedStateHandle = SavedStateHandle(),
        recorder = recorder,
        inProgressRepository = inProgress,
        completedRepository = completed,
        suggestedName = { suggestion }
    )

    @Test
    fun `자유그림은 시작할 때 고른 주제를 이름 기본값으로 제안하고 저장하지는 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.FREE_DRAWING)

        val state = suggestingViewModel("별").uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals("별", state.suggestedName)
        assertNull(state.name)
        assertNull(savedSummaries().single().name)
    }

    @Test
    fun `동그라미 챌린지는 주제를 제안하지 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath())

        val state = suggestingViewModel("별").uiState.value as ChallengeResultUiState.Calculated

        assertNull(state.suggestedName)
    }

    @Test
    fun `고른 주제가 없으면 제안하지 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.FREE_DRAWING)
        val none = suggestingViewModel(null).uiState.value as ChallengeResultUiState.Calculated
        assertNull(none.suggestedName)
    }

    private suspend fun saveInProgress(
        path: List<RoutePoint>,
        startedAtMillis: Long = START,
        mode: DrawingMode = DrawingMode.CIRCLE
    ) {
        inProgress.start(mode, startedAtMillis)
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
        assertTrue(state.result.score!!.score >= 90)
        assertEquals(END - START, state.result.durationMillis)
        val summary = savedSummaries().single()
        assertEquals(state.result.score!!.score, summary.score)
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
    fun `위치가 부족한 0점 도전은 결과만 보여주고 저장하지 않으며 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(listOf(RoutePoint(37.5665, 126.978, START)))

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(0, state.result.score!!.score)
        assertEquals(ZeroScoreReason.INSUFFICIENT_POINTS, state.result.score!!.zeroReason)
        assertEquals(ResultSaveStatus.NOT_SAVED, state.saveStatus)
        assertTrue(savedSummaries().isEmpty())
        assertNull(inProgressDrawing())
    }

    @Test
    fun `움직였지만 원이 아닌 0점 도전은 저장한다`() = runTest(dispatcher) {
        saveInProgress(
            TestPaths.straightOutAndBack(100.0).mapIndexed { i, it ->
                RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
            }
        )

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(listOf(state.result.score!!.score), savedSummaries().map { it.score })
    }

    @Test
    fun `저장에 실패하면 결과를 유지하고 진행 중 기록을 남긴다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        completed.failure = IllegalStateException("저장 실패")

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.FAILED, state.saveStatus)
        assertTrue(state.result.score!!.score >= 90)
        assertEquals(circlePath().size, inProgressDrawing()?.path?.size)
        // 최고 기록 조회와 저장이 각각 실패한다.
        assertEquals(2, storageFailures.size)
        assertNull(state.bestComparison)
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
        assertEquals(BestRecordComparison.FirstRecord, state.bestComparison)
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

    @Test
    fun `저장한 결과에 앞뒤 공백을 제거한 이름을 붙인다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val viewModel = viewModel()

        viewModel.saveName("  첫 동그라미  ")

        val state = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(NameSaveStatus.SAVED, state.nameSaveStatus)
        assertEquals("첫 동그라미", state.name)
        assertEquals("첫 동그라미", savedSummaries().single().name)
    }

    @Test
    fun `동그라미 챌린지는 이름을 비워 두어도 저장되고 이름 없이 남는다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val viewModel = viewModel()

        viewModel.saveName("   ")

        val state = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(NameSaveStatus.SAVED, state.nameSaveStatus)
        assertNull(state.name)
        assertNull(savedSummaries().single().name)
    }

    @Test
    fun `20자를 넘는 이름은 저장하지 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val viewModel = viewModel()

        viewModel.saveName("가".repeat(21))

        val state = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(NameSaveStatus.IDLE, state.nameSaveStatus)
        assertNull(savedSummaries().single().name)
    }

    @Test
    fun `완료 기록을 저장하지 못했으면 이름을 저장하지 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        completed.failure = IllegalStateException("저장 실패")
        val viewModel = viewModel()

        viewModel.saveName("이름")

        val state = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(ResultSaveStatus.FAILED, state.saveStatus)
        assertEquals(NameSaveStatus.IDLE, state.nameSaveStatus)
    }

    @Test
    fun `이름 저장에 실패하면 실패 상태이고 다시 저장할 수 있다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val viewModel = viewModel()
        completed.failure = IllegalStateException("이름 저장 실패")

        viewModel.saveName("이름")

        val failed = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(NameSaveStatus.FAILED, failed.nameSaveStatus)
        assertNull(failed.name)
        assertEquals(1, storageFailures.size)

        completed.failure = null
        viewModel.saveName("이름")

        val saved = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(NameSaveStatus.SAVED, saved.nameSaveStatus)
        assertEquals("이름", saved.name)
    }

    @Test
    fun `화면이 다시 만들어지면 저장한 이름을 보여준다`() = runTest(dispatcher) {
        saveInProgress(circlePath())
        val handle = SavedStateHandle()
        viewModel(handle).saveName("한강 한 바퀴")

        val restored = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals("한강 한 바퀴", restored.name)
        assertEquals(1, savedSummaries().size)
    }

    private suspend fun saveCompleted(score: Int) {
        completed.save(
            CompletedDrawing(
                mode = DrawingMode.CIRCLE,
                completedAtMillis = START - 1_000_000L - score,
                path = emptyList(),
                score = score,
                metrics = emptyMap(),
                distanceMeters = 0.0,
                durationMillis = 0L
            )
        )
    }

    @Test
    fun `다른 기록이 없으면 첫 기록으로 표시한다`() = runTest(dispatcher) {
        saveInProgress(circlePath())

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(BestRecordComparison.FirstRecord, state.bestComparison)
    }

    @Test
    fun `이전 최고 점수보다 높으면 신기록과 차이를 표시한다`() = runTest(dispatcher) {
        saveCompleted(40)
        saveCompleted(70)
        saveInProgress(circlePath())

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        val score = state.result.score!!.score
        assertEquals(BestRecordComparison.NewRecord(70, score - 70), state.bestComparison)
    }

    @Test
    fun `이전 최고 점수보다 낮으면 차이를 표시하고 다시 만들어져도 같은 비교를 보여준다`() = runTest(dispatcher) {
        saveCompleted(100)
        // 출발점 40m 앞에서 끝낸 원은 출발·도착 근접도가 낮아 만점보다 낮다.
        saveInProgress(
            TestPaths.openCircle(100.0, gapMeters = 40.0).mapIndexed { i, it ->
                RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
            }
        )
        val handle = SavedStateHandle()
        val first = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated
        val gap = 100 - first.result.score!!.score

        val recreated = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(BestRecordComparison.BelowBest(100, gap), first.bestComparison)
        assertEquals(first.bestComparison, recreated.bestComparison)
    }

    @Test
    fun `자유그림은 점수 없이 자유그림 모드로 저장하고 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.FREE_DRAWING)

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(DrawingMode.FREE_DRAWING, state.result.mode)
        assertNull(state.result.score)
        assertNull(state.result.zeroReasonText)
        val summary = savedSummaries().single()
        assertEquals(DrawingMode.FREE_DRAWING, summary.mode)
        assertEquals(0, summary.score)
        assertEquals(314.0, summary.distanceMeters, 2.0)
        assertNull(inProgressDrawing())
    }

    @Test
    fun `이동하지 않은 자유그림은 저장하지 않고 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(stationaryPath(), mode = DrawingMode.FREE_DRAWING)

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.NOT_SAVED, state.saveStatus)
        assertEquals(END - START, state.result.durationMillis)
        assertTrue(savedSummaries().isEmpty())
        assertNull(inProgressDrawing())
    }

    @Test
    fun `저장하지 않은 자유그림은 화면이 다시 만들어져도 저장하지 않는다`() = runTest(dispatcher) {
        saveInProgress(stationaryPath(), mode = DrawingMode.FREE_DRAWING)
        val handle = SavedStateHandle()
        viewModel(handle)

        val recreated = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.NOT_SAVED, recreated.saveStatus)
        assertEquals(DrawingMode.FREE_DRAWING, recreated.result.mode)
        assertEquals(END - START, recreated.result.durationMillis)
        assertTrue(savedSummaries().isEmpty())
    }

    @Test
    fun `한 자리에 머문 동그라미 챌린지는 저장하지 않고 화면이 다시 만들어져도 저장하지 않는다`() = runTest(dispatcher) {
        saveInProgress(
            TestPaths.stationary(30).mapIndexed { i, it ->
                RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
            }
        )
        val handle = SavedStateHandle()

        val state = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated
        val recreated = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ZeroScoreReason.NO_MOVEMENT, state.result.score!!.zeroReason)
        assertEquals(ResultSaveStatus.NOT_SAVED, state.saveStatus)
        assertEquals(ResultSaveStatus.NOT_SAVED, recreated.saveStatus)
        assertEquals(DrawingMode.CIRCLE, recreated.result.mode)
        assertTrue(savedSummaries().isEmpty())
        assertNull(inProgressDrawing())
    }

    @Test
    fun `프로세스가 다시 만들어진 뒤에도 저장하지 못한 자유그림을 자유그림으로 저장한다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.FREE_DRAWING)
        inProgress.finish(END)

        val unsaved = requireNotNull(recorder.unsavedFinish())
        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(DrawingMode.FREE_DRAWING, unsaved.mode)
        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        assertEquals(DrawingMode.FREE_DRAWING, savedSummaries().single().mode)
        assertNull(recorder.unsavedFinish())
    }

    @Test
    fun `자유그림은 이름이 필수라 비워 두면 저장하지 않고 이름을 입력하면 저장한다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.FREE_DRAWING)
        val viewModel = viewModel()

        viewModel.saveName("   ")

        val empty = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(NameSaveStatus.IDLE, empty.nameSaveStatus)
        assertNull(savedSummaries().single().name)

        viewModel.saveName(" 남산 하트 ")

        val named = viewModel.uiState.value as ChallengeResultUiState.Calculated
        assertEquals(NameSaveStatus.SAVED, named.nameSaveStatus)
        assertEquals("남산 하트", savedSummaries().single().name)
    }

    @Test
    fun `자유그림은 점수가 없어 최고 기록과 비교하지 않는다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.FREE_DRAWING)

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertNull(state.bestComparison)
    }

    private fun stationaryPath() = TestPaths.stationary(5).mapIndexed { i, it ->
        RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val END = START + 185_000L
    }

    @Test
    fun `차지한 땅따먹기는 면적과 함께 땅따먹기 모드로 저장하고 진행 중 기록을 지운다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.TERRITORY)

        val state = viewModel().uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, state.saveStatus)
        val claimed = state.result.territory as TerritoryResult.Claimed
        assertNull(state.bestComparison)
        val summary = savedSummaries().single()
        assertEquals(DrawingMode.TERRITORY, summary.mode)
        assertEquals(claimed.areaSquareMeters, summary.territoryAreaSquareMeters!!, 0.0)
        assertNull(inProgressDrawing())
    }

    @Test
    fun `닫히지 않은 땅따먹기는 이유를 남기고 저장하지 않으며 진행 중 기록을 지운다`() = runTest(dispatcher) {
        val path = TestPaths.openCircle(60.0, 55.0).mapIndexed { i, it ->
            RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
        }
        saveInProgress(path, mode = DrawingMode.TERRITORY)
        val handle = SavedStateHandle()

        val state = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated
        val recreated = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.NOT_SAVED, state.saveStatus)
        assertTrue(state.result.territory is TerritoryResult.NotClosed)
        assertEquals(ResultSaveStatus.NOT_SAVED, recreated.saveStatus)
        assertEquals(DrawingMode.TERRITORY, recreated.result.mode)
        // 진행 중 기록을 지운 뒤라 경로가 없어 다시 판정하지 않는다.
        assertNull(recreated.result.territory)
        assertTrue(savedSummaries().isEmpty())
        assertNull(inProgressDrawing())
    }

    @Test
    fun `저장한 땅따먹기는 화면이 다시 만들어지면 저장한 기록으로 같은 영역을 보여준다`() = runTest(dispatcher) {
        saveInProgress(circlePath(), mode = DrawingMode.TERRITORY)
        val handle = SavedStateHandle()
        val first = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        val recreated = viewModel(handle).uiState.value as ChallengeResultUiState.Calculated

        assertEquals(ResultSaveStatus.SAVED, recreated.saveStatus)
        assertEquals(first.result.territory, recreated.result.territory)
        assertEquals(1, savedSummaries().size)
    }
}
