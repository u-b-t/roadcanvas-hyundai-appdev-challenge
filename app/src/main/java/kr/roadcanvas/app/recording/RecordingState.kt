package kr.roadcanvas.app.recording

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint

/** 기록 세션 상태 */
sealed interface RecordingState {
    /** 기록 중인 도전이 없다. */
    data object Idle : RecordingState

    /** 기록 중이다. [distanceMeters]는 [path]의 점 사이 거리의 합이다. */
    data class Recording(
        val mode: DrawingMode,
        val startedAtMillis: Long,
        val path: List<RoutePoint>,
        val distanceMeters: Double
    ) : RecordingState

    /**
     * 사용자가 종료했다. 위치 구독은 멈췄고 진행 중 기록은 저장소에 남아 있다.
     * 결과 화면(#19)이 완료 기록을 저장한 뒤 진행 중 기록을 지운다.
     */
    data class Finished(
        val mode: DrawingMode,
        val startedAtMillis: Long,
        val endedAtMillis: Long,
        val path: List<RoutePoint>,
        val distanceMeters: Double
    ) : RecordingState {
        /** 시작부터 종료까지의 실제 경과 시간 */
        val durationMillis: Long get() = endedAtMillis - startedAtMillis
    }

    /** 기록을 시작하거나 이어가지 못했다. */
    data class Failed(val cause: Throwable) : RecordingState
}

/** 기록을 새로 시작할지, 저장된 진행 중 기록을 이어갈지 */
sealed interface RecordingRequest {
    data class New(val mode: DrawingMode) : RecordingRequest

    data object Resume : RecordingRequest
}
