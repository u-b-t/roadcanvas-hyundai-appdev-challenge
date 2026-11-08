package kr.roadcanvas.app.recording

/**
 * [ChallengeRecorder.run]을 화면과 상관없이 실행한다.
 * 앱은 위치 타입 포그라운드 서비스에서, 테스트는 코루틴에서 실행한다.
 */
interface RecordingRunner {
    /** 기록을 실행한다. 이미 실행 중이면 무시한다. */
    fun run(request: RecordingRequest)

    /** 실행 중인 기록과 위치 구독을 멈춘다. */
    fun stop()
}
