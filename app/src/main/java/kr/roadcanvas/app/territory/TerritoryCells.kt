package kr.roadcanvas.app.territory

import kr.roadcanvas.app.score.GeoPoint

/** 한 번에 획득할 수 있는 최대 칸 수(약 5.8km²). 넘으면 획득하지 않는다. */
const val TERRITORY_MAX_CELLS_PER_CAPTURE = 400

/** 칸 평균 면적(m²). 서울시청 위도에서 계산한 칸 면적이며 칸 수를 면적으로 환산할 때 쓴다. */
val TERRITORY_CELL_AREA_SQUARE_METERS: Double =
    TerritoryGrid.cellAreaSquareMeters(latitude = 37.5665)

/** 칸 [cellCount]개의 환산 면적(m²) */
fun territoryAreaOf(cellCount: Long): Double = cellCount * TERRITORY_CELL_AREA_SQUARE_METERS

/** 차지한 다각형을 서버 칸으로 바꾼 결과 */
sealed interface TerritoryCellsResult {
    /** 획득할 칸 ID 목록([TerritoryGridCell.id]). 중복 없이 정렬되어 있고 1개 이상이다. */
    data class Cells(val cells: List<String>) : TerritoryCellsResult

    /** 칸이 [maxCells]개를 넘어 획득하지 않는다. 화면은 "영역이 너무 커요"라고 안내한다. */
    data class TooLarge(val maxCells: Int) : TerritoryCellsResult
}

/**
 * [boundary] 다각형 안에 중심이 들어오는 격자 칸을 구한다. 그런 칸이 없으면 다각형 무게중심이 속한 칸 1개다.
 * [boundary]는 `TerritoryResult.Claimed.boundary`처럼 첫 점을 끝에 반복하지 않는 단순 다각형이다.
 * 칸이 [maxCells]개를 넘으면 [TerritoryCellsResult.TooLarge]다.
 */
fun territoryCellsOf(
    boundary: List<GeoPoint>,
    maxCells: Int = TERRITORY_MAX_CELLS_PER_CAPTURE
): TerritoryCellsResult {
    require(boundary.size >= 3) { "다각형 꼭짓점이 셋 미만입니다." }
    val (rows, cols) = TerritoryGrid.cellRanges(
        GeoBounds(
            south = boundary.minOf { it.latitude },
            west = boundary.minOf { it.longitude },
            north = boundary.maxOf { it.latitude },
            east = boundary.maxOf { it.longitude }
        )
    )
    val cells = sortedSetOf<String>()
    for (row in rows) {
        for (col in cols) {
            val cell = TerritoryGridCell(row, col)
            if (!contains(boundary, cell.center)) continue
            cells += cell.id
            if (cells.size > maxCells) return TerritoryCellsResult.TooLarge(maxCells)
        }
    }
    if (cells.isEmpty()) cells += TerritoryGrid.cellOf(centroid(boundary)).id
    return TerritoryCellsResult.Cells(cells.toList())
}

/** 짝수-홀수 규칙의 점 포함 판정. 칸 크기 범위에서는 위도·경도를 평면으로 봐도 된다. */
private fun contains(polygon: List<GeoPoint>, point: GeoPoint): Boolean {
    var inside = false
    var previous = polygon.last()
    for (current in polygon) {
        if ((current.latitude > point.latitude) != (previous.latitude > point.latitude)) {
            val crossLongitude = current.longitude +
                (point.latitude - current.latitude) / (previous.latitude - current.latitude) *
                (previous.longitude - current.longitude)
            if (point.longitude < crossLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}

/** 다각형 면적 무게중심. 면적이 0이면 꼭짓점 평균이다. */
private fun centroid(polygon: List<GeoPoint>): GeoPoint {
    val origin = polygon.first()
    var twiceArea = 0.0
    var x = 0.0
    var y = 0.0
    var previous = polygon.last()
    for (current in polygon) {
        val x0 = previous.longitude - origin.longitude
        val y0 = previous.latitude - origin.latitude
        val x1 = current.longitude - origin.longitude
        val y1 = current.latitude - origin.latitude
        val cross = x0 * y1 - x1 * y0
        twiceArea += cross
        x += (x0 + x1) * cross
        y += (y0 + y1) * cross
        previous = current
    }
    if (twiceArea == 0.0) {
        return GeoPoint(
            polygon.map {
                it.latitude
            }.average(),
            polygon.map { it.longitude }.average()
        )
    }
    return GeoPoint(origin.latitude + y / (3 * twiceArea), origin.longitude + x / (3 * twiceArea))
}
