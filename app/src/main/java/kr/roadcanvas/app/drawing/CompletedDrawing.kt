package kr.roadcanvas.app.drawing

/**
 * 완료한 도전 기록.
 *
 * [metrics]는 점수 세부 지표 이름과 값이다. 점수 계산 모델과 따로 바뀔 수 있도록 이름으로 저장한다.
 * 저장할 때 [id]는 쓰지 않고 저장소가 새 ID를 정한다.
 */
data class CompletedDrawing(
    val id: Long = NEW_ID,
    val mode: DrawingMode,
    val completedAtMillis: Long,
    val path: List<RoutePoint>,
    val score: Int,
    val metrics: Map<String, Double>,
    val distanceMeters: Double,
    val durationMillis: Long
) {
    companion object {
        const val NEW_ID = 0L
    }
}

/** 목록에 보여줄 완료 기록 요약. 경로와 세부 지표는 포함하지 않는다. */
data class CompletedDrawingSummary(
    val id: Long,
    val mode: DrawingMode,
    val completedAtMillis: Long,
    val score: Int,
    val distanceMeters: Double,
    val durationMillis: Long
)

/** 앱이 종료돼도 이어서 기록할 수 있도록 보관하는 진행 중인 도전 */
data class InProgressDrawing(
    val mode: DrawingMode,
    val startedAtMillis: Long,
    val path: List<RoutePoint>
)
