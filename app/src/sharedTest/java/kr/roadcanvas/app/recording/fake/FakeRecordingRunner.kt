package kr.roadcanvas.app.recording.fake

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingRequest
import kr.roadcanvas.app.recording.RecordingRunner

/** 포그라운드 서비스 대신 코루틴에서 기록을 실행하는 대체 구현 */
class FakeRecordingRunner(
    private val recorder: () -> ChallengeRecorder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : RecordingRunner {
    private var job: Job? = null
    private val mutableRequests = MutableStateFlow(emptyList<RecordingRequest>())

    /** 실행을 요청받은 순서. 실행 중이라 무시한 요청도 포함한다. */
    val requests: StateFlow<List<RecordingRequest>> = mutableRequests.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    @Synchronized
    override fun run(request: RecordingRequest) {
        mutableRequests.update { it + request }
        if (isRunning) return
        job = scope.launch { recorder().run(request) }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
    }

    fun reset() {
        stop()
        mutableRequests.value = emptyList()
    }
}
