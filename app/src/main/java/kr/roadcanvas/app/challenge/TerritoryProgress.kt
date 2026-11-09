package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.recording.distanceTo
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryConfig
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.territory.TERRITORY_MAX_CELLS_PER_CAPTURE
import kr.roadcanvas.app.territory.TerritoryCellsResult
import kr.roadcanvas.app.territory.formatCells
import kr.roadcanvas.app.territory.territoryCellsOf

/** 진행 화면의 예상 영역을 다시 계산하는 간격. 경로가 길어도 계산이 화면 갱신을 막지 않도록 제한한다. */
const val TERRITORY_ESTIMATE_REFRESH_MILLIS = 3_000L

/** 출발점이 가까워졌다고 알리는 거리(m) */
const val START_NEAR_METERS = 200.0

/** 출발점에 거의 왔다고 알리는 거리(m). 땅따먹기 닫힘 허용 거리와 같다. */
const val START_ARRIVING_METERS = 50.0

/**
 * 출발점 근처로 "돌아왔다"고 보려면 그 거리보다 이만큼 더 멀리 나갔다 와야 한다(m).
 * 출발 직후 위치 흔들림으로 경계를 넘나들 때 안내하지 않게 한다.
 */
const val START_LEFT_MARGIN_METERS = 30.0

/** 예상 면적이 이보다 작으면(m²) 둘러싼 영역이 없는 것으로 보고 표시하지 않는다. */
private const val MIN_ESTIMATE_AREA_SQUARE_METERS = 1.0

const val ESTIMATED_AREA_LABEL = "예상 면적"
const val ESTIMATED_CELLS_LABEL = "예상 칸"
const val APPROACH_NEAR_GUIDANCE = "출발점까지 200m 안이에요"
const val APPROACH_ARRIVING_GUIDANCE = "출발점 50m 안! 정차 후 종료하면 땅을 차지해요"
const val APPROACH_NEAR_SPEECH = "출발점까지 200미터 남았어요"
const val APPROACH_ARRIVING_SPEECH = "출발점에 거의 왔어요. 정차한 뒤 종료하면 땅을 차지해요"

/**
 * 지금까지 그린 경로를 출발점까지 곧게 이어 닫았을 때 차지할 영역.
 * [boundary]는 결과 화면과 같은 규칙(가장 큰 고리 하나)으로 고른 다각형이다.
 * [cellCount]는 서버 칸 수이며 [TERRITORY_MAX_CELLS_PER_CAPTURE]를 넘으면 null이다.
 */
data class TerritoryEstimate(
    val areaSquareMeters: Double,
    val boundary: List<GeoPoint>,
    val cellCount: Int?
)

/** 출발점과의 거리로 나눈 단계. 뒤에 올수록 출발점에 가깝다. */
enum class StartApproach {
    /** 출발점에서 멀거나 아직 출발점을 충분히 떠나지 않았다. */
    AWAY,

    /** 출발점을 떠났다가 [START_NEAR_METERS] 안으로 돌아왔다. */
    NEAR,

    /** 출발점을 떠났다가 [START_ARRIVING_METERS] 안으로 돌아왔다. */
    ARRIVING
}

// 닫힘 거리와 최소 면적을 따지지 않고 지금 끝점을 출발점에 이어 닫는다.
private val estimateCalculator = TerritoryCalculator(
    TerritoryConfig(
        closureToleranceMeters = Double.POSITIVE_INFINITY,
        minAreaSquareMeters = 0.0,
        minValidPoints = 3
    )
)

/** 기록 중인 [path]의 예상 영역. 둘러싼 영역이 없으면 null이다. */
fun territoryEstimateOf(path: List<RoutePoint>): TerritoryEstimate? {
    val result = estimateCalculator.calculate(path.map { GeoPoint(it.latitude, it.longitude) })
    val claimed = result as? TerritoryResult.Claimed ?: return null
    if (claimed.areaSquareMeters < MIN_ESTIMATE_AREA_SQUARE_METERS) return null
    val cellCount = when (val cells = territoryCellsOf(claimed.boundary)) {
        is TerritoryCellsResult.Cells -> cells.cells.size
        is TerritoryCellsResult.TooLarge -> null
    }
    return TerritoryEstimate(claimed.areaSquareMeters, claimed.boundary, cellCount)
}

/** [path]의 마지막 점이 출발점에 얼마나 가까워졌는지. 그 거리보다 충분히 멀리 나갔다 온 때만 가깝다고 본다. */
fun startApproachOf(path: List<RoutePoint>): StartApproach {
    if (path.size < 2) return StartApproach.AWAY
    val start = path.first()
    val current = path.last().distanceTo(start)
    val farthest = path.maxOf { it.distanceTo(start) }
    fun returnedWithin(meters: Double) =
        current <= meters && farthest >= meters + START_LEFT_MARGIN_METERS
    return when {
        returnedWithin(START_ARRIVING_METERS) -> StartApproach.ARRIVING
        returnedWithin(START_NEAR_METERS) -> StartApproach.NEAR
        else -> StartApproach.AWAY
    }
}

/** 출발점에 더 가까운 단계로 들어왔을 때 읽어 줄 안내. 그대로거나 멀어졌으면 null이다. */
fun approachSpeechOf(previous: StartApproach, current: StartApproach): String? =
    if (current <= previous) {
        null
    } else {
        current.speech
    }

/** 화면에 크게 보여 줄 접근 안내 */
val StartApproach.guidance: String?
    get() = when (this) {
        StartApproach.AWAY -> null
        StartApproach.NEAR -> APPROACH_NEAR_GUIDANCE
        StartApproach.ARRIVING -> APPROACH_ARRIVING_GUIDANCE
    }

private val StartApproach.speech: String?
    get() = when (this) {
        StartApproach.AWAY -> null
        StartApproach.NEAR -> APPROACH_NEAR_SPEECH
        StartApproach.ARRIVING -> APPROACH_ARRIVING_SPEECH
    }

/** 예상 면적. 영역이 없으면 `-`다. */
fun formatEstimatedArea(estimate: TerritoryEstimate?): String =
    estimate?.let { formatArea(it.areaSquareMeters) } ?: "-"

/** 예상 칸 수. 영역이 없으면 `-`, 한 번에 차지할 수 있는 칸을 넘으면 `400칸 초과`다. */
fun formatEstimatedCells(estimate: TerritoryEstimate?): String = when {
    estimate == null -> "-"
    estimate.cellCount == null -> "${formatCells(TERRITORY_MAX_CELLS_PER_CAPTURE.toLong())} 초과"
    else -> formatCells(estimate.cellCount.toLong())
}
