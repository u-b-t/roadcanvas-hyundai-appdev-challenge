package kr.roadcanvas.app.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.InProgressDrawingRepository
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.vehicle.LocationProvider

/**
 * 도전 하나의 경로 기록 세션. 받은 위치를 순서대로 경로에 더하고 진행 중 기록 저장소에 이어서 저장한다.
 * 도전을 시작한 뒤 새로 구독해 도착한 위치만 기록하므로 시작 전에 이미 있던 위치(준비 화면이 받은 위치 등)는 쓰지 않는다.
 * 앱 전체에서 하나를 공유하며, 위치 구독은 [RecordingRunner]가 [run]을 실행하는 동안에만 한다.
 *
 * 종료하면 위치 구독을 멈추고 진행 중 기록에 종료 시각만 남긴다. 결과 화면(#19)이 그 기록으로
 * 완료 기록을 저장하는 데 성공한 뒤 [InProgressDrawingRepository.clear]로 지운다.
 */
class ChallengeRecorder(
    private val locationProvider: LocationProvider,
    private val repository: InProgressDrawingRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onStorageFailure: (Throwable) -> Unit = {}
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)

    val state: StateFlow<RecordingState> = mutableState.asStateFlow()

    /** 기록을 시작하거나 이어간 뒤 종료·포기할 때까지 위치를 기록한다. */
    suspend fun run(request: RecordingRequest) {
        val ready = when (request) {
            is RecordingRequest.New -> start(request.mode)
            RecordingRequest.Resume -> resume()
        }
        if (ready) record()
    }

    /**
     * 종료하지 않은 진행 중 기록이 메모리나 저장소에 있는지.
     * 앱을 다시 실행할 때 진행 화면으로 갈지 정한다.
     */
    suspend fun hasInProgress(): Boolean = inProgressMode() != null

    /** 종료하지 않은 진행 중 기록의 모드. 없으면 null이다. */
    suspend fun inProgressMode(): DrawingMode? {
        (mutableState.value as? RecordingState.Recording)?.let { return it.mode }
        val saved = (repository.load() as? StorageResult.Success)?.value ?: return null
        return saved.mode.takeIf { saved.endedAtMillis == null }
    }

    /**
     * 종료했지만 결과 화면이 아직 완료 기록으로 저장하지 못한 도전. 없으면 null이다.
     * 앱을 다시 열 때 결과 화면으로 돌아가는 데 쓴다. 같은 프로세스면 메모리의 종료 상태를,
     * 프로세스가 다시 만들어졌으면 저장소에 남긴 종료 시각으로 종료 상태를 다시 만든다.
     */
    suspend fun unsavedFinish(): RecordingState.Finished? {
        val current = mutableState.value
        if (current is RecordingState.Recording) return null
        val saved = (repository.load() as? StorageResult.Success)?.value ?: return null
        if (current is RecordingState.Finished) {
            return current.takeIf { it.startedAtMillis == saved.startedAtMillis }
        }
        val endedAtMillis = saved.endedAtMillis ?: return null
        return RecordingState.Finished(
            mode = saved.mode,
            startedAtMillis = saved.startedAtMillis,
            endedAtMillis = endedAtMillis,
            path = saved.path,
            distanceMeters = pathDistanceMeters(saved.path)
        )
    }

    /**
     * 포그라운드 서비스를 시작하지 못하는 등 기록을 실행할 수 없을 때 실패로 알린다.
     * 시작하려던 [mode]를 알면 함께 남겨 진행 화면이 그 모드의 준비 화면으로 돌아가게 한다.
     */
    fun fail(cause: Throwable, mode: DrawingMode? = null) {
        mutableState.value = RecordingState.Failed(cause, mode)
    }

    /**
     * 위치 구독을 멈출 수 있도록 종료 상태로 바꾸고 진행 중 기록에 종료 시각을 남긴다. 기록 중이 아니면 null이다.
     * 종료 시각을 저장하지 못해도 종료한다. 같은 프로세스에서는 메모리의 종료 상태로 결과 화면에 돌아갈 수 있다.
     */
    suspend fun finish(): RecordingState.Finished? = mutex.withLock {
        val current = mutableState.value as? RecordingState.Recording ?: return null
        val finished = RecordingState.Finished(
            mode = current.mode,
            startedAtMillis = current.startedAtMillis,
            endedAtMillis = clock(),
            path = current.path,
            distanceMeters = current.distanceMeters
        )
        val saved = repository.finish(finished.endedAtMillis)
        if (saved is StorageResult.Failure) onStorageFailure(saved.cause)
        finished.also { mutableState.value = it }
    }

    /** 진행 중 기록을 지운다. 지우지 못하면 기록을 계속한다. */
    suspend fun giveUp(): StorageResult<Unit> = mutex.withLock {
        repository.clear().also {
            if (it is StorageResult.Success) mutableState.value = RecordingState.Idle
        }
    }

    private suspend fun start(mode: DrawingMode): Boolean = mutex.withLock {
        val startedAtMillis = clock()
        when (val result = repository.start(mode, startedAtMillis)) {
            is StorageResult.Success -> {
                mutableState.value =
                    RecordingState.Recording(mode, startedAtMillis, emptyList(), 0.0)
                true
            }
            is StorageResult.Failure -> {
                mutableState.value = RecordingState.Failed(result.cause, mode)
                false
            }
        }
    }

    private suspend fun resume(): Boolean = mutex.withLock {
        if (mutableState.value is RecordingState.Recording) return true
        when (val result = repository.load()) {
            is StorageResult.Success -> {
                // 이미 종료한 도전은 이어서 기록하지 않는다. 결과 화면이 완료 기록으로 저장한다.
                val drawing = result.value?.takeIf { it.endedAtMillis == null } ?: return false
                mutableState.value = RecordingState.Recording(
                    mode = drawing.mode,
                    startedAtMillis = drawing.startedAtMillis,
                    path = drawing.path,
                    distanceMeters = pathDistanceMeters(drawing.path)
                )
                true
            }
            is StorageResult.Failure -> {
                mutableState.value = RecordingState.Failed(result.cause)
                false
            }
        }
    }

    /**
     * 종료·포기로 기록 중 상태가 끝나면 위치 구독을 멈춘다. 끊긴 구간은 다음 점과 직선으로 이어진다.
     *
     * 경로 점의 시각은 위치를 받은 시각이다. SDK가 준 위치 시각은 시스템 시계와 어긋날 수 있으므로
     * 기록 여부 판정이나 점 시각에 쓰지 않는다. 위치 시각이 없거나 과거·미래로 어긋나도 기록은 멈추지 않는다.
     */
    private suspend fun record() {
        locationProvider.locations().takeWhile { sample ->
            mutex.withLock {
                val current = mutableState.value as? RecordingState.Recording
                    ?: return@withLock false
                val point = RoutePoint(sample.latitude, sample.longitude, clock())
                val last = current.path.lastOrNull()
                if (last != null && last.isSameLocationAs(point)) return@withLock true
                // 저장에 실패해도 기록은 이어간다. 그 점은 메모리의 경로에만 남는다.
                val saved = repository.appendPoints(listOf(point))
                if (saved is StorageResult.Failure) onStorageFailure(saved.cause)
                mutableState.value = current.copy(
                    path = current.path + point,
                    distanceMeters = current.distanceMeters + (last?.distanceTo(point) ?: 0.0)
                )
                true
            }
        }.collect()
    }
}
