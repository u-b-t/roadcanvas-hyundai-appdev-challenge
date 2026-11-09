package kr.roadcanvas.app.drawing

/**
 * 완료한 도전 기록.
 *
 * [metrics]는 점수 세부 지표 이름과 값이다. 점수 계산 모델과 따로 바뀔 수 있도록 이름으로 저장한다.
 * 저장할 때 [id]는 쓰지 않고 저장소가 새 ID를 정한다.
 * [name]은 사용자가 붙인 이름이며 붙이지 않았으면 null이다.
 */
data class CompletedDrawing(
    val id: Long = NEW_ID,
    val mode: DrawingMode,
    val completedAtMillis: Long,
    val path: List<RoutePoint>,
    val score: Int,
    val metrics: Map<String, Double>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val name: String? = null
) {
    companion object {
        const val NEW_ID = 0L
    }
}

/** 땅따먹기 완료 기록이 차지한 영역의 면적(m²)을 저장하는 세부 지표 이름. 저장된 기록을 읽을 때 쓰므로 바꾸지 않는다. */
const val TERRITORY_AREA_METRIC = "territoryAreaSquareMeters"

/** 땅따먹기 완료 기록이 차지한 영역의 둘레(m)를 저장하는 세부 지표 이름 */
const val TERRITORY_PERIMETER_METRIC = "territoryPerimeterMeters"

/** 땅따먹기 기록이 차지한 영역의 면적(m²). 땅따먹기가 아니거나 저장하지 않았으면 null이다. */
val CompletedDrawing.territoryAreaSquareMeters: Double?
    get() = metrics[TERRITORY_AREA_METRIC]

/** 땅따먹기 기록이 차지한 영역의 둘레(m). 땅따먹기가 아니거나 저장하지 않았으면 null이다. */
val CompletedDrawing.territoryPerimeterMeters: Double?
    get() = metrics[TERRITORY_PERIMETER_METRIC]

/**
 * 목록에 보여줄 완료 기록 요약. 전체 경로와 세부 지표 대신 썸네일용 축약 경로([routeThumbnail])를 담는다.
 * 경로 점이 없는 기록은 [thumbnail]이 비어 있다.
 * [territoryAreaSquareMeters]는 땅따먹기 기록이 차지한 면적(m²)이며 다른 모드는 null이다.
 */
data class CompletedDrawingSummary(
    val id: Long,
    val mode: DrawingMode,
    val completedAtMillis: Long,
    val score: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val name: String? = null,
    val thumbnail: List<RoutePoint> = emptyList(),
    val territoryAreaSquareMeters: Double? = null
)

/**
 * 앱이 종료돼도 이어서 기록할 수 있도록 보관하는 진행 중인 도전.
 * [endedAtMillis]가 있으면 사용자가 종료했지만 아직 완료 기록으로 저장하지 않은 도전이다.
 */
data class InProgressDrawing(
    val mode: DrawingMode,
    val startedAtMillis: Long,
    val path: List<RoutePoint>,
    val endedAtMillis: Long? = null
)
