package kr.roadcanvas.app.recording

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeRecorderTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val locationProvider = FakeLocationProvider()
    private val repository = FakeInProgressDrawingRepository()
    private var now = START
    private val storageFailures = mutableListOf<Throwable>()
    private val recorder =
        ChallengeRecorder(locationProvider, repository, clock = { now }, onStorageFailure = {
            storageFailures += it
        })

    private suspend fun TestScope.runRecorder(request: RecordingRequest): Job {
        val job = backgroundScope.launch(dispatcher) { recorder.run(request) }
        if (recorder.state.value is RecordingState.Recording) {
            locationProvider.subscriberCount.first { it > 0 }
        }
        return job
    }

    private suspend fun send(latitude: Double, longitude: Double = LONGITUDE) {
        now += 1_000
        locationProvider.sendLocation(LocationSample(latitude, longitude, now))
    }

    private suspend fun savedPath() =
        (repository.load() as StorageResult.Success).value?.path.orEmpty()

    @Test
    fun `시작하면 진행 중 기록을 만들고 받은 위치를 순서대로 기록한다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        send(37.5665)
        send(37.5674)

        val state = recorder.state.value as RecordingState.Recording
        assertEquals(START, state.startedAtMillis)
        assertEquals(listOf(37.5665, 37.5674), state.path.map { it.latitude })
        assertEquals(state.path, savedPath())
        assertEquals(pathDistanceMeters(state.path), state.distanceMeters, 1e-9)
    }

    @Test
    fun `같은 좌표가 연달아 오면 하나만 남긴다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        send(37.5665)
        send(37.5665)
        send(37.5674)
        send(37.5665)

        val path = (recorder.state.value as RecordingState.Recording).path
        assertEquals(listOf(37.5665, 37.5674, 37.5665), path.map { it.latitude })
        assertEquals(path, savedPath())
    }

    @Test
    fun `거리는 점 사이 거리의 합이다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        send(37.5665)
        send(37.5674)
        send(37.5683)

        val state = recorder.state.value as RecordingState.Recording
        assertEquals(200.14, state.distanceMeters, 0.02)
    }

    @Test
    fun `종료하면 경과 시간을 정하고 이후 위치는 기록하지 않는다`() = runTest(dispatcher) {
        val job = runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)
        send(37.5674)
        now = START + 65_000

        val finished = recorder.finish()

        assertEquals(65_000L, finished?.durationMillis)
        assertEquals(2, finished?.path?.size)
        send(37.5683)
        assertTrue(job.isCompleted)
        assertEquals(2, savedPath().size)
        assertEquals(finished, recorder.state.value)
    }

    @Test
    fun `기록 중이 아니면 종료할 수 없다`() = runTest(dispatcher) {
        assertNull(recorder.finish())
        assertEquals(RecordingState.Idle, recorder.state.value)
    }

    @Test
    fun `포기하면 진행 중 기록을 지우고 기록을 멈춘다`() = runTest(dispatcher) {
        val job = runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)

        val result = recorder.giveUp()

        assertTrue(result is StorageResult.Success)
        assertEquals(RecordingState.Idle, recorder.state.value)
        assertNull((repository.load() as StorageResult.Success).value)
        send(37.5674)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `진행 중 기록을 지우지 못하면 포기하지 않고 기록을 계속한다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)
        repository.failure = IllegalStateException("삭제 실패")

        val result = recorder.giveUp()

        assertTrue(result is StorageResult.Failure)
        repository.failure = null
        send(37.5674)
        assertEquals(2, (recorder.state.value as RecordingState.Recording).path.size)
    }

    @Test
    fun `다시 실행하면 저장된 기록을 불러와 이어서 기록한다`() = runTest(dispatcher) {
        repository.start(DrawingMode.CIRCLE, START)
        repository.appendPoints(
            listOf(RoutePoint(37.5665, LONGITUDE, START), RoutePoint(37.5674, LONGITUDE, START))
        )

        runRecorder(RecordingRequest.Resume)
        send(37.5683)

        val state = recorder.state.value as RecordingState.Recording
        assertEquals(START, state.startedAtMillis)
        assertEquals(listOf(37.5665, 37.5674, 37.5683), state.path.map { it.latitude })
        assertEquals(state.path, savedPath())
        assertEquals(200.14, state.distanceMeters, 0.02)
    }

    @Test
    fun `이어갈 기록이 없으면 위치를 구독하지 않는다`() = runTest(dispatcher) {
        val job = runRecorder(RecordingRequest.Resume)

        assertTrue(job.isCompleted)
        assertEquals(0, locationProvider.requestCount.value)
        assertEquals(RecordingState.Idle, recorder.state.value)
        assertFalse(recorder.hasInProgress())
    }

    @Test
    fun `진행 중 기록이 저장되어 있으면 이어갈 기록이 있다`() = runTest(dispatcher) {
        repository.start(DrawingMode.CIRCLE, START)

        assertTrue(recorder.hasInProgress())
    }

    @Test
    fun `종료한 뒤 진행 중 기록이 남아 있으면 저장하지 못한 종료 기록이 있다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)
        val finished = recorder.finish()

        assertEquals(finished, recorder.unsavedFinish())

        repository.clear()

        assertNull(recorder.unsavedFinish())
        assertFalse(recorder.hasInProgress())
    }

    @Test
    fun `기록 중이거나 다른 도전이 저장되어 있으면 저장하지 못한 종료 기록이 없다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        assertNull(recorder.unsavedFinish())

        recorder.finish()
        repository.start(DrawingMode.CIRCLE, START + 1)

        assertNull(recorder.unsavedFinish())
    }

    @Test
    fun `진행 중 기록을 만들지 못하면 실패로 알리고 위치를 구독하지 않는다`() = runTest(dispatcher) {
        val error = IllegalStateException("저장 실패")
        repository.failure = error

        val job = runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        assertTrue(job.isCompleted)
        assertEquals(RecordingState.Failed(error), recorder.state.value)
        assertEquals(0, locationProvider.requestCount.value)
    }

    @Test
    fun `경로 점 저장에 실패해도 기록은 이어간다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        val error = IllegalStateException("추가 실패")
        repository.failure = error

        send(37.5665)

        assertEquals(1, (recorder.state.value as RecordingState.Recording).path.size)
        assertEquals(listOf<Throwable>(error), storageFailures)
    }

    @Test
    fun `다시 시작하면 이전 진행 중 기록을 지우고 새로 기록한다`() = runTest(dispatcher) {
        repository.start(DrawingMode.CIRCLE, 1L)
        repository.appendPoints(listOf(RoutePoint(1.0, 1.0, 1L)))

        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        val state = recorder.state.value as RecordingState.Recording
        assertEquals(START, state.startedAtMillis)
        assertEquals(emptyList<RoutePoint>(), state.path)
        assertEquals(emptyList<RoutePoint>(), savedPath())
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val LONGITUDE = 126.978
    }
}
