package kr.roadcanvas.app.challenge

import java.util.Locale
import kotlin.math.roundToLong
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.territory.TerritoryLoss

const val AREA_LABEL = "면적"
const val PERIMETER_LABEL = "둘레"
const val TERRITORY_NOT_SAVED = "영역을 차지하지 못해 저장하지 않았어요"

/** 이 면적(m²) 이상은 km² 단위로 보여준다. */
private const val SQUARE_METERS_PER_SQUARE_KILOMETER = 1_000_000L

/** 면적을 1km² 미만은 `12,345 m²`처럼 m² 단위 정수로, 그 이상은 `1.23 km²`처럼 소수 둘째 자리까지 보여준다. */
fun formatArea(squareMeters: Double): String {
    val rounded = squareMeters.coerceAtLeast(0.0).roundToLong()
    return if (rounded < SQUARE_METERS_PER_SQUARE_KILOMETER) {
        String.format(Locale.ROOT, "%,d m²", rounded)
    } else {
        String.format(Locale.ROOT, "%.2f km²", squareMeters / SQUARE_METERS_PER_SQUARE_KILOMETER)
    }
}

/** 영역을 차지하지 못한 이유. 기준값은 판정 결과에 담긴 값을 쓴다. 차지했으면 null이다. */
fun territoryFailureText(result: TerritoryResult): String? = when (result) {
    is TerritoryResult.Claimed -> null
    is TerritoryResult.NotClosed ->
        "출발점에서 ${formatDistance(result.gapMeters)} 떨어진 곳에서 끝나 영역이 닫히지 않았어요. " +
            "출발점 ${formatDistance(result.toleranceMeters)} 안으로 돌아와 끝내 주세요"
    is TerritoryResult.TooSmall ->
        "둘러싼 면적 ${formatArea(result.areaSquareMeters)}이 " +
            "최소 ${formatArea(result.minAreaSquareMeters)}보다 작아요"
    TerritoryResult.InsufficientPoints -> "기록한 위치가 부족해 영역을 판정할 수 없어요"
}

/** 빼앗은 상대 안내. 예: `드라이버-a님에게서 3칸, 드라이버-b님에게서 1칸을 빼앗았어요`. 빼앗은 칸이 없으면 null이다. */
fun territoryLossText(losses: List<TerritoryLoss>): String? {
    if (losses.isEmpty()) return null
    return losses.joinToString(", ") { "${it.nickname}님에게서 ${it.cells.size}칸" } + "을 빼앗았어요"
}
