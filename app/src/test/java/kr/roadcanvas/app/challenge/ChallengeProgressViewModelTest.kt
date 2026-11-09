package kr.roadcanvas.app.challenge

import kotlin.math.PI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingRequest
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.recording.fake.FakeRecordingRunner
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeDrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val locationProvider = FakeLocationProvider()
    private val repository = FakeInProgressDrawingRepository()
    private val restrictionProvider = FakeDrivingRestrictionProvider()
    private var now = START
    private val recorder = ChallengeRecorder(locationProvider, repository, clock = { now })
    private val runner = FakeRecordingRunner({ recorder }, CoroutineScope(dispatcher))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runner.stop()
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ChallengeProgressViewModel(recorder, runner, restrictionProvider, clock = { now })

    private suspend fun startRecording() {
        runner.run(RecordingRequest.New(DrawingMode.CIRCLE))
        locationProvider.subscriberCount.first { it > 0 }
    }

    private fun TestScope.subscribe(viewModel: ChallengeProgressViewModel) =
        backgroundScope.launch { viewModel.uiState.collect {} }

    @Test
    fun `기록 중이 아닌 상태로 진행 화면이 열리면 저장된 기록을 이어간다`() = runTest(dispatcher) {
        viewModel()

        assertEquals(listOf<RecordingRequest>(RecordingRequest.Resume), runner.requests.value)
    }

    @Test
    fun `이미 기록 중이면 이어가기를 요청하지 않는다`() = runTest(dispatcher) {
        startRecording()

        viewModel()

        assertEquals(
            listOf<RecordingRequest>(RecordingRequest.New(DrawingMode.CIRCLE)),
            runner.requests.value
        )
    }

    @Test
    fun `화면은 1초에 한 번 경로와 경과 시간을 갱신한다`() = runTest(dispatcher) {
        startRecording()
        val viewModel = viewModel()
        subscribe(viewModel)

        now += 500
        locationProvider.sendLocation(LocationSample(37.5665, 126.978, now))
        assertEquals(0, viewModel.uiState.value.path.size)

        now += 500
        advanceTimeBy(PROGRESS_REFRESH_MILLIS + 1)
        assertEquals(1, viewModel.uiState.value.path.size)
        assertEquals(1_000L, viewModel.uiState.value.elapsedMillis)
    }

    @Test
    fun `땅따먹기는 일정 간격으로 예상 영역을 계산한다`() = runTest(dispatcher) {
        runner.run(RecordingRequest.New(DrawingMode.TERRITORY))
        locationProvider.subscriberCount.first { it > 0 }
        val viewModel = ChallengeProgressViewModel(
            recorder,
            runner,
            restrictionProvider,
            clock = { now },
            estimateDispatcher = dispatcher
        )
        subscribe(viewModel)
        assertNull(viewModel.uiState.value.territoryEstimate)

        TestPaths.semicircle(60.0).forEach {
            locationProvider.sendLocation(LocationSample(it.latitude, it.longitude, now))
        }
        advanceTimeBy(TERRITORY_ESTIMATE_REFRESH_MILLIS + 1)

        val state = viewModel.uiState.value
        assertTrue(state.showsTerritoryProgress)
        assertEquals(PI * 60 * 60 / 2, state.territoryEstimate!!.areaSquareMeters, 100.0)
        // 반원 끝점이 출발점에서 가장 먼 점(지름 120m)이라 아직 돌아오는 중이 아니다.
        assertEquals(StartApproach.AWAY, state.startApproach)
    }

    @Test
    fun `땅따먹기가 아니면 예상 영역을 계산하지 않는다`() = runTest(dispatcher) {
        startRecording()
        val viewModel = viewModel()
        subscribe(viewModel)
        TestPaths.semicircle(60.0).forEach {
            locationProvider.sendLocation(LocationSample(it.latitude, it.longitude, now))
        }

        advanceTimeBy(TERRITORY_ESTIMATE_REFRESH_MILLIS + 1)

        assertNull(viewModel.uiState.value.territoryEstimate)
        assertFalse(viewModel.uiState.value.showsTerritoryProgress)
    }

    @Test
    fun `종료하면 기록을 멈추고 종료한 기록을 넘긴다`() = runTest(dispatcher) {
        startRecording()
        locationProvider.sendLocation(LocationSample(37.5665, 126.978, now))
        now += 30_000
        var finished: RecordingState.Finished? = null

        viewModel().finish { finished = it }

        assertEquals(30_000L, finished?.durationMillis)
        assertEquals(1, finished?.path?.size)
        assertFalse(runner.isRunning)
        assertEquals(0, locationProvider.subscriberCount.value)
        assertEquals(1, (repository.load() as StorageResult.Success).value?.path?.size)
    }

    @Test
    fun `조작이 제한되면 종료와 포기를 하지 않는다`() = runTest(dispatcher) {
        startRecording()
        restrictionProvider.setRestriction(DrivingRestriction.RESTRICTED)
        val viewModel = viewModel()
        var finished: RecordingState.Finished? = null
        var gaveUp = false

        viewModel.finish { finished = it }
        viewModel.giveUp { gaveUp = true }

        assertNull(finished)
        assertFalse(gaveUp)
        assertTrue(runner.isRunning)
        assertTrue(recorder.state.value is RecordingState.Recording)
    }

    @Test
    fun `포기하면 진행 중 기록을 지우고 기록을 멈춘다`() = runTest(dispatcher) {
        startRecording()
        var gaveUp = false

        viewModel().giveUp { gaveUp = true }

        assertTrue(gaveUp)
        assertFalse(runner.isRunning)
        assertNull((repository.load() as StorageResult.Success).value)
    }

    @Test
    fun `진행 중 기록을 지우지 못하면 실패를 알리고 기록을 계속한다`() = runTest(dispatcher) {
        startRecording()
        repository.failure = IllegalStateException("삭제 실패")
        val viewModel = viewModel()
        var gaveUp = false

        viewModel.giveUp { gaveUp = true }

        assertFalse(gaveUp)
        assertTrue(viewModel.giveUpFailed.value)
        assertTrue(runner.isRunning)
        viewModel.dismissGiveUpFailure()
        assertFalse(viewModel.giveUpFailed.value)
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
