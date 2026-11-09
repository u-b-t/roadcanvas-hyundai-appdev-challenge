package kr.roadcanvas.app.challenge

import kotlin.math.roundToInt
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.TERRITORY_AREA_METRIC
import kr.roadcanvas.app.drawing.TERRITORY_PERIMETER_METRIC
import kr.roadcanvas.app.map.RouteCircle
import kr.roadcanvas.app.map.RoutePoint as MapPoint
import kr.roadcanvas.app.recording.pathDistanceMeters
import kr.roadcanvas.app.score.CircleScoreCalculator
import kr.roadcanvas.app.score.CircleScoreConfig
import kr.roadcanvas.app.score.CircleScoreMetrics
import kr.roadcanvas.app.score.CircleScoreResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.ZeroScoreReason

/**
 * 자유그림을 완료 기록으로 저장할 최소 이동 거리(m). 이보다 짧으면 이동하지 않은 것으로 보고 저장하지 않는다.
 * 정차 중 위치 오차로 생기는 짧은 경로를 그림으로 남기지 않기 위한 값이다.
 */
const val FREE_DRAWING_MIN_DISTANCE_METERS = 10.0

/**
 * 종료한 도전 하나의 결과. 점수를 매기는 모드만 [score]가 있고, 땅따먹기만 영역 판정 결과 [territory]가 있다.
 */
data class ChallengeResult(
    val mode: DrawingMode,
    val path: List<RoutePoint>,
    val completedAtMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double,
    val score: CircleScoreResult?,
    /** 0점 사유 안내. 점수를 계산한 설정의 기준값을 넣는다. 0점이 아니거나 점수가 없으면 null이다. */
    val zeroReasonText: String?,
    val territory: TerritoryResult? = null
) {
    /**
     * 완료 기록으로 저장할지. 그림이 없는 도전은 저장하지 않는다.
     * 점수를 매기는 모드는 위치가 부족하거나 거의 움직이지 않아 0점이면 저장하지 않고, 움직였지만 원이 아니거나 작아 0점이면 저장한다.
     * 땅따먹기는 영역을 차지했을 때만 저장한다.
     * 그 밖의 점수가 없는 모드는 이동 거리가 [FREE_DRAWING_MIN_DISTANCE_METERS]보다 짧으면 저장하지 않는다.
     */
    val savable: Boolean
        get() = when {
            mode.scored ->
                score?.zeroReason != ZeroScoreReason.INSUFFICIENT_POINTS &&
                    score?.zeroReason != ZeroScoreReason.NO_MOVEMENT
            mode == DrawingMode.TERRITORY -> territory is TerritoryResult.Claimed
            else -> distanceMeters >= FREE_DRAWING_MIN_DISTANCE_METERS
        }

    /**
     * 완료 기록으로 저장할 값. 0점이거나 점수가 없는 모드는 세부 지표 없이 점수 0으로 저장한다.
     * 차지한 땅따먹기는 면적과 둘레를 세부 지표로 저장한다.
     */
    fun toCompletedDrawing() = CompletedDrawing(
        mode = mode,
        completedAtMillis = completedAtMillis,
        path = path,
        score = score?.score ?: 0,
        metrics = score?.metrics?.toNamedValues()
            ?: (territory as? TerritoryResult.Claimed)?.toNamedValues().orEmpty(),
        distanceMeters = distanceMeters,
        durationMillis = durationMillis
    )
}

/**
 * 시작부터 종료까지 기록한 [path]로 결과를 만든다. 점수를 매기는 모드만 점수를 계산하고 땅따먹기만 영역을 판정한다.
 */
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
    calculator = calculator
)

/** 저장한 완료 기록을 다시 보여줄 때 같은 경로로 점수와 0점 사유, 땅따먹기 영역을 다시 구한다. */
fun challengeResultOf(
    drawing: CompletedDrawing,
    calculator: CircleScoreCalculator = CircleScoreCalculator()
) = scoredResult(
    mode = drawing.mode,
    path = drawing.path,
    completedAtMillis = drawing.completedAtMillis,
    durationMillis = drawing.durationMillis,
    distanceMeters = drawing.distanceMeters,
    calculator = calculator
)

private fun scoredResult(
    mode: DrawingMode,
    path: List<RoutePoint>,
    completedAtMillis: Long,
    durationMillis: Long,
    distanceMeters: Double,
    calculator: CircleScoreCalculator
): ChallengeResult {
    val score = if (mode.scored) {
        calculator.calculate(path.map { GeoPoint(it.latitude, it.longitude) })
    } else {
        null
    }
    val territory = if (mode == DrawingMode.TERRITORY) {
        TerritoryCalculator().calculate(path.map { GeoPoint(it.latitude, it.longitude) })
    } else {
        null
    }
    return ChallengeResult(
        mode = mode,
        path = path,
        completedAtMillis = completedAtMillis,
        durationMillis = durationMillis,
        distanceMeters = distanceMeters,
        score = score,
        zeroReasonText = score?.zeroReason?.let { zeroScoreReasonText(it, calculator.config) },
        territory = territory
    )
}

/** 저장한 땅따먹기 기록이 차지한 영역의 경계. 경로로 다시 판정하며 땅따먹기가 아니거나 차지하지 못했으면 null이다. */
fun territoryBoundaryOf(
    drawing: CompletedDrawing,
    calculator: TerritoryCalculator = TerritoryCalculator()
): List<GeoPoint>? {
    if (drawing.mode != DrawingMode.TERRITORY) return null
    val territory = calculator.calculate(drawing.path.map { GeoPoint(it.latitude, it.longitude) })
    return (territory as? TerritoryResult.Claimed)?.boundary
}

/** 차지한 영역을 저장소에 기록하는 이름과 값 */
fun TerritoryResult.Claimed.toNamedValues(): Map<String, Double> = mapOf(
    TERRITORY_AREA_METRIC to areaSquareMeters,
    TERRITORY_PERIMETER_METRIC to perimeterMeters
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

/** 점수 구성 항목. [ratio]는 0~1이고 [tip]은 이 항목이 가장 낮을 때 다음 도전에서 고칠 점이다. */
data class ScoreComponent(val label: String, val ratio: Double, val tip: String) {
    /** 화면에 보이는 0~100% 값 */
    val percent: Int get() = (ratio * 100).roundToInt().coerceIn(0, 100)
}

const val CIRCULARITY_LABEL = "원형도"
const val CLOSURE_LABEL = "출발·도착 근접도"
const val ROTATION_LABEL = "회전 완성도"

/** 이 비율 이상이면 고칠 점으로 안내하지 않는다. */
private const val TIP_THRESHOLD = 0.9

/** 점수를 이루는 세 항목. 0점이라 세부 지표가 없으면 빈 목록이다. */
val ChallengeResult.scoreComponents: List<ScoreComponent>
    get() = score?.metrics?.let {
        listOf(
            ScoreComponent(CIRCULARITY_LABEL, it.circularity, "반지름을 일정하게 유지해 보세요"),
            ScoreComponent(CLOSURE_LABEL, it.closure, "출발점으로 돌아와 끝내 보세요"),
            ScoreComponent(ROTATION_LABEL, it.rotationCompleteness, "한 바퀴를 끝까지 돌아 보세요")
        )
    }.orEmpty()

/** 가장 낮은 항목의 고칠 점. 모든 항목이 충분히 높거나 0점이면 null이다. */
val ChallengeResult.improvementTip: String?
    get() = scoreComponents.minByOrNull { it.ratio }?.takeIf { it.ratio < TIP_THRESHOLD }?.tip

/** 지도에 경로와 함께 그릴 맞춤 원. 0점이라 원을 맞추지 않았으면 null이다. */
val ChallengeResult.fitCircle: RouteCircle?
    get() = score?.metrics?.let {
        RouteCircle(MapPoint(it.center.latitude, it.center.longitude), it.radiusMeters)
    }

/** 같은 모드의 이전 최고 점수와 비교한 결과 */
sealed interface BestRecordComparison {
    val text: String

    data object FirstRecord : BestRecordComparison {
        override val text = "첫 기록이에요"
    }

    data class NewRecord(val previousBest: Int, val gain: Int) : BestRecordComparison {
        override val text = "신기록! 이전 최고 ${previousBest}점보다 ${gain}점 높아요"
    }

    data class TiedBest(val best: Int) : BestRecordComparison {
        override val text = "최고 기록 ${best}점과 같아요"
    }

    data class BelowBest(val best: Int, val gap: Int) : BestRecordComparison {
        override val text = "최고 기록 ${best}점까지 ${gap}점 남았어요"
    }
}

/** 이 도전의 [score]를 다른 기록의 최고 점수 [previousBest]와 비교한다. 다른 기록이 없으면 첫 기록이다. */
fun bestRecordComparisonOf(score: Int, previousBest: Int?): BestRecordComparison = when {
    previousBest == null -> BestRecordComparison.FirstRecord
    score > previousBest -> BestRecordComparison.NewRecord(previousBest, score - previousBest)
    score == previousBest -> BestRecordComparison.TiedBest(previousBest)
    else -> BestRecordComparison.BelowBest(previousBest, previousBest - score)
}

/** 완료 기록 저장 단계 */
enum class ResultSaveStatus {
    SAVING,
    SAVED,
    FAILED,

    /** 이동하지 않은 도전이라 저장하지 않고 진행 중 기록만 지웠다([ChallengeResult.savable]). */
    NOT_SAVED
}

/** 저장한 완료 기록에 이름을 붙이는 단계 */
enum class NameSaveStatus {
    /** 이름 저장을 요청하지 않았다. */
    IDLE,
    SAVING,
    SAVED,
    FAILED
}

/** 결과 화면 상태: 계산 중 → 저장 중 → 저장됨 또는 저장 실패. 저장하지 않는 기록은 바로 저장 안 함이다. */
sealed interface ChallengeResultUiState {
    /** 종료한 기록을 불러와 점수를 계산하는 중 */
    data object Calculating : ChallengeResultUiState

    /** 종료한 기록을 찾거나 불러오지 못했다. */
    data object Unavailable : ChallengeResultUiState

    /**
     * [name]은 저장한 완료 기록의 이름이며 붙이지 않았으면 null이다.
     * [bestComparison]은 다른 기록을 불러오지 못하면 null이다.
     */
    data class Calculated(
        val result: ChallengeResult,
        val saveStatus: ResultSaveStatus,
        val name: String? = null,
        val nameSaveStatus: NameSaveStatus = NameSaveStatus.IDLE,
        val bestComparison: BestRecordComparison? = null
    ) : ChallengeResultUiState
}
