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
    fun `시작 전에 이미 있던 위치는 기록하지 않는다`() = runTest(dispatcher) {
        // 준비 화면처럼 시작 전에 위치를 받고 있던 다른 수집자
        val received = mutableListOf<LocationSample>()
        backgroundScope.launch(dispatcher) {
            locationProvider.locations().collect { received += it }
        }
        locationProvider.subscriberCount.first { it > 0 }
        send(37.5000)

        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        locationProvider.subscriberCount.first { it > 1 }
        send(37.5665)

        assertEquals(2, received.size)
        val path = (recorder.state.value as RecordingState.Recording).path
        assertEquals(listOf(37.5665), path.map { it.latitude })
        assertEquals(path, savedPath())
    }

    @Test
    fun `위치 시각이 시작 전이거나 미래이거나 없어도 받은 시각으로 기록한다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        val sampleTimes = listOf(START - DAY, START + 365 * DAY, 0L, -1L)

        val receivedTimes = sampleTimes.mapIndexed { i, sampleTime ->
            now += 1_000
            locationProvider.sendLocation(
                LocationSample(37.5665 + i * 0.0009, LONGITUDE, sampleTime)
            )
            now
        }

        val path = (recorder.state.value as RecordingState.Recording).path
        assertEquals(receivedTimes, path.map { it.timeMillis })
        assertEquals(path, savedPath())
        assertEquals(300.2, pathDistanceMeters(path), 0.1)
    }

    @Test
    fun `시스템 시계가 위치 시각보다 앞서도 기록은 멈추지 않는다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))

        // 위치 시각이 계속 시작 시각보다 10분 이른 상황
        repeat(3) { i ->
            now += 1_000
            locationProvider.sendLocation(
                LocationSample(37.5665 + i * 0.0009, LONGITUDE, START - 600_000 + i * 1_000)
            )
        }

        assertEquals(3, (recorder.state.value as RecordingState.Recording).path.size)
    }

    @Test
    fun `이어서 기록할 때 위치 시각이 원래 시작 시각보다 일러도 기록한다`() = runTest(dispatcher) {
        repository.start(DrawingMode.CIRCLE, START)
        repository.appendPoints(listOf(RoutePoint(37.5665, LONGITUDE, START + 1_000)))
        now = START + 120_000

        runRecorder(RecordingRequest.Resume)
        locationProvider.sendLocation(LocationSample(37.5683, LONGITUDE, START - DAY))

        val state = recorder.state.value as RecordingState.Recording
        assertEquals(listOf(37.5665, 37.5683), state.path.map { it.latitude })
        assertEquals(START + 120_000, state.path.last().timeMillis)
        assertEquals(200.14, state.distanceMeters, 0.02)
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
    fun `종료하면 진행 중 기록에 종료 시각을 남긴다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)
        now = START + 65_000

        recorder.finish()

        val saved = (repository.load() as StorageResult.Success).value
        assertEquals(START + 65_000, saved?.endedAtMillis)
        assertEquals(1, saved?.path?.size)
    }

    @Test
    fun `종료 시각을 저장하지 못해도 종료하고 실패를 알린다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)
        val error = IllegalStateException("종료 저장 실패")
        repository.failure = error

        val finished = recorder.finish()

        assertEquals(finished, recorder.state.value)
        assertEquals(listOf<Throwable>(error), storageFailures)
        repository.failure = null
        assertEquals(finished, recorder.unsavedFinish())
    }

    @Test
    fun `프로세스가 다시 만들어져도 종료한 기록은 저장하지 못한 종료 기록으로 돌려준다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.CIRCLE))
        send(37.5665)
        send(37.5674)
        now = START + 65_000
        val finished = recorder.finish()

        val restarted = ChallengeRecorder(locationProvider, repository, clock = { now })

        assertEquals(finished, restarted.unsavedFinish())
        assertFalse(restarted.hasInProgress())
        assertEquals(RecordingState.Idle, restarted.state.value)
    }

    @Test
    fun `종료하지 않은 기록은 프로세스가 다시 만들어져도 저장하지 못한 종료 기록이 아니다`() = runTest(dispatcher) {
        repository.start(DrawingMode.CIRCLE, START)
        repository.appendPoints(listOf(RoutePoint(37.5665, LONGITUDE, START)))

        assertNull(recorder.unsavedFinish())
        assertTrue(recorder.hasInProgress())
    }

    @Test
    fun `종료한 기록은 이어서 기록하지 않는다`() = runTest(dispatcher) {
        repository.start(DrawingMode.CIRCLE, START)
        repository.appendPoints(listOf(RoutePoint(37.5665, LONGITUDE, START)))
        repository.finish(START + 65_000)

        val job = runRecorder(RecordingRequest.Resume)

        assertTrue(job.isCompleted)
        assertEquals(0, locationProvider.requestCount.value)
        assertEquals(RecordingState.Idle, recorder.state.value)
        assertEquals(1, savedPath().size)
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
        assertEquals(RecordingState.Failed(error, DrawingMode.CIRCLE), recorder.state.value)
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

    @Test
    fun `자유그림도 같은 기록 흐름으로 기록하고 종료하면 모드를 남긴다`() = runTest(dispatcher) {
        runRecorder(RecordingRequest.New(DrawingMode.FREE_DRAWING))
        send(37.5665)
        send(37.5674)

        assertEquals(DrawingMode.FREE_DRAWING, recorder.inProgressMode())
        val finished = requireNotNull(recorder.finish())

        assertEquals(DrawingMode.FREE_DRAWING, finished.mode)
        assertEquals(2, finished.path.size)
        assertEquals(
            DrawingMode.FREE_DRAWING,
            (repository.load() as StorageResult.Success).value?.mode
        )
        assertNull(recorder.inProgressMode())
    }

    @Test
    fun `저장된 자유그림을 이어서 기록하면 자유그림으로 기록한다`() = runTest(dispatcher) {
        repository.start(DrawingMode.FREE_DRAWING, START)
        repository.appendPoints(listOf(RoutePoint(37.5665, LONGITUDE, START)))

        assertEquals(DrawingMode.FREE_DRAWING, recorder.inProgressMode())
        runRecorder(RecordingRequest.Resume)
        send(37.5674)

        val state = recorder.state.value as RecordingState.Recording
        assertEquals(DrawingMode.FREE_DRAWING, state.mode)
        assertEquals(2, state.path.size)
    }

    @Test
    fun `기록을 시작하지 못하면 시작하려던 모드를 실패 상태에 남긴다`() = runTest(dispatcher) {
        repository.failure = IllegalStateException("저장 실패")

        runRecorder(RecordingRequest.New(DrawingMode.FREE_DRAWING))

        assertEquals(DrawingMode.FREE_DRAWING, (recorder.state.value as RecordingState.Failed).mode)
        recorder.fail(IllegalStateException("서비스 시작 실패"), DrawingMode.CIRCLE)
        assertEquals(DrawingMode.CIRCLE, (recorder.state.value as RecordingState.Failed).mode)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val LONGITUDE = 126.978
        const val DAY = 86_400_000L
    }
}
