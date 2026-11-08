package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.recording.pathDistanceMeters
import kr.roadcanvas.app.score.CircleScoreCalculator
import kr.roadcanvas.app.score.CircleScoreConfig
import kr.roadcanvas.app.score.CircleScoreMetrics
import kr.roadcanvas.app.score.CircleScoreResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.ZeroScoreReason

/** 종료한 도전 하나의 점수 계산 결과 */
data class ChallengeResult(
    val mode: DrawingMode,
    val path: List<RoutePoint>,
    val completedAtMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double,
    val score: CircleScoreResult,
    /** 0점 사유 안내. 점수를 계산한 설정의 기준값을 넣는다. 0점이 아니면 null이다. */
    val zeroReasonText: String?
) {
    /** 완료 기록으로 저장할 값. 0점이면 세부 지표 없이 저장한다. */
    fun toCompletedDrawing() = CompletedDrawing(
        mode = mode,
        completedAtMillis = completedAtMillis,
        path = path,
        score = score.score,
        metrics = score.metrics?.toNamedValues().orEmpty(),
        distanceMeters = distanceMeters,
        durationMillis = durationMillis
    )
}

/** 시작부터 종료까지 기록한 [path]로 점수를 계산한다. */
fun challengeResultOf(
    mode: DrawingMode,
    path: List<RoutePoint>,
    startedAtMillis: Long,
    endedAtMillis: Long,
    calculator: CircleScoreCalculator = CircleScoreCalculator()
) = scoredResult(
    mode = mode,
    path = path,
    completedAtMillis = endedAtMillis,
    durationMillis = (endedAtMillis - startedAtMillis).coerceAtLeast(0L),
    distanceMeters = pathDistanceMeters(path),
    score = calculator.calculate(path.map { GeoPoint(it.latitude, it.longitude) }),
    calculator = calculator
)

/** 저장한 완료 기록을 다시 보여줄 때 같은 경로로 점수와 0점 사유를 다시 구한다. */
fun challengeResultOf(
    drawing: CompletedDrawing,
    calculator: CircleScoreCalculator = CircleScoreCalculator()
) = scoredResult(
    mode = drawing.mode,
    path = drawing.path,
    completedAtMillis = drawing.completedAtMillis,
    durationMillis = drawing.durationMillis,
    distanceMeters = drawing.distanceMeters,
    score = calculator.calculate(drawing.path.map { GeoPoint(it.latitude, it.longitude) }),
    calculator = calculator
)

private fun scoredResult(
    mode: DrawingMode,
    path: List<RoutePoint>,
    completedAtMillis: Long,
    durationMillis: Long,
    distanceMeters: Double,
    score: CircleScoreResult,
    calculator: CircleScoreCalculator
) = ChallengeResult(
    mode = mode,
    path = path,
    completedAtMillis = completedAtMillis,
    durationMillis = durationMillis,
    distanceMeters = distanceMeters,
    score = score,
    zeroReasonText = score.zeroReason?.let { zeroScoreReasonText(it, calculator.config) }
)

/** 세부 지표를 저장소에 기록하는 이름과 값. 이름은 저장된 기록을 읽을 때 쓰므로 바꾸지 않는다. */
fun CircleScoreMetrics.toNamedValues(): Map<String, Double> = mapOf(
    "circularity" to circularity,
    "closure" to closure,
    "rotationCompleteness" to rotationCompleteness,
    "radiusMeters" to radiusMeters,
    "radialRmsRatio" to radialRmsRatio,
    "closureDistanceMeters" to closureDistanceMeters,
    "rotationRadians" to rotationRadians,
    "pathLengthMeters" to pathLengthMeters
)

/** 0점 사유 안내. 기준값은 [config]에서 가져온다. */
fun zeroScoreReasonText(reason: ZeroScoreReason, config: CircleScoreConfig): String =
    when (reason) {
        ZeroScoreReason.INSUFFICIENT_POINTS -> "기록한 위치가 ${config.minValidPoints}개보다 적어요"
        ZeroScoreReason.NO_MOVEMENT -> "이동 거리가 ${config.minPathLengthMeters.toInt()}m보다 짧아요"
        ZeroScoreReason.NOT_CIRCULAR -> "경로가 직선에 가까워 원으로 볼 수 없어요"
        ZeroScoreReason.TOO_SMALL -> "원의 지름이 ${config.minDiameterMeters.toInt()}m보다 작아요"
    }

/** 완료 기록 저장 단계 */
enum class ResultSaveStatus {
    SAVING,
    SAVED,
    FAILED
}

/** 결과 화면 상태: 계산 중 → 저장 중 → 저장됨 또는 저장 실패 */
sealed interface ChallengeResultUiState {
    /** 종료한 기록을 불러와 점수를 계산하는 중 */
    data object Calculating : ChallengeResultUiState

    /** 종료한 기록을 찾거나 불러오지 못했다. */
    data object Unavailable : ChallengeResultUiState

    data class Calculated(val result: ChallengeResult, val saveStatus: ResultSaveStatus) :
        ChallengeResultUiState
}
