package kr.roadcanvas.app.territory

import kr.roadcanvas.app.score.GeoPoint

/** 결과 지도에서 주변 칸을 읽는 여백(m). 결과 영역을 감싸는 사각형을 동서남북으로 이만큼 넓힌다. */
const val TERRITORY_RESULT_NEARBY_MARGIN_METERS = 200.0

/** 이번 획득의 칸을 반영 직전 소유자로 나눈 것 */
enum class ResultCellKind {
    /** 소유자가 없던 칸이거나, 아직 반영하지 않았거나 이전 소유자를 모르는 칸 */
    CAPTURED,

    /** 다른 사용자에게서 빼앗은 칸 */
    STOLEN,

    /** 이미 내 칸이던 칸 */
    KEPT
}

/** 이번 획득의 칸 [id]. 빼앗은 칸이면 [previousOwner]가 이전 소유자 닉네임이다. */
data class ResultCell(val id: String, val kind: ResultCellKind, val previousOwner: String? = null)

/** 결과 지도에 그릴 칸. [captured]는 이번 획득의 칸이고 [nearby]는 그 밖의 주변 칸이다. */
data class TerritoryResultCells(val captured: List<ResultCell>, val nearby: List<TerritoryCell>)

/**
 * 이번 획득의 칸 [captureCells]를 반영 결과 [outcome]으로 나누고, 주변 칸 [nearby]에서 획득한 칸을 뺀다.
 * [outcome]이 없거나 이전 소유자를 모르면(다시 불러온 결과) 모든 칸을 [ResultCellKind.CAPTURED]로 둔다.
 */
fun territoryResultCellsOf(
    captureCells: List<String>,
    outcome: TerritoryCaptureOutcome?,
    nearby: List<TerritoryCell>
): TerritoryResultCells {
    val previousOwners = outcome?.losses.orEmpty()
        .flatMap { loss -> loss.cells.map { it to loss.nickname } }
        .toMap()
    val kept = outcome?.ownCellIds.orEmpty().toSet()
    val captured = captureCells.map { id ->
        when (id) {
            in previousOwners -> ResultCell(id, ResultCellKind.STOLEN, previousOwners[id])
            in kept -> ResultCell(id, ResultCellKind.KEPT)
            else -> ResultCell(id, ResultCellKind.CAPTURED)
        }
    }
    val ids = captureCells.toSet()
    return TerritoryResultCells(captured, nearby.filter { it.id !in ids })
}

/** 결과 영역 [boundary]를 감싸는 사각형을 동서남북으로 [marginMeters]만큼 넓힌 범위 */
fun boundsAroundArea(
    boundary: List<GeoPoint>,
    marginMeters: Double = TERRITORY_RESULT_NEARBY_MARGIN_METERS
): GeoBounds {
    val south = boundary.minOf { it.latitude }
    val north = boundary.maxOf { it.latitude }
    val west = boundary.minOf { it.longitude }
    val east = boundary.maxOf { it.longitude }
    val center = GeoPoint((south + north) / 2, (west + east) / 2)
    val margin = boundsAround(center, marginMeters)
    val latitudeDelta = margin.north - center.latitude
    val longitudeDelta = margin.east - center.longitude
    return GeoBounds(
        south = south - latitudeDelta,
        west = west - longitudeDelta,
        north = north + latitudeDelta,
        east = east + longitudeDelta
    )
}
