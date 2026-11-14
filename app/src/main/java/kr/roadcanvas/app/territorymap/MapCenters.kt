package kr.roadcanvas.app.territorymap

import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.territory.TerritoryGrid

/** 내 땅이 모인 곳을 나누는 묶음 한 변의 칸 수. 40칸은 서울 기준 약 4.8km 정사각형이다. */
const val MY_LAND_CLUSTER_CELLS = 40

/**
 * 영역을 차지한 땅따먹기 기록이 가장 많이 모인 곳의 가운데. 기록마다 축약 경로의 가운데를 구해
 * [MY_LAND_CLUSTER_CELLS]칸 정사각형으로 묶고, 기록이 가장 많은 칸의 가운데들을 평균한다.
 * 개수가 같으면 [summaries]의 앞쪽(최신) 기록이 있는 칸이다. 차지한 기록이 없으면 null이다.
 */
fun myLandCenter(summaries: List<CompletedDrawingSummary>): RoutePoint? {
    val centers = summaries
        .filter { it.mode == DrawingMode.TERRITORY && (it.territoryAreaSquareMeters ?: 0.0) > 0.0 }
        .mapNotNull { it.thumbnail.center() }
    if (centers.isEmpty()) return null
    val groups = centers.groupBy {
        val cell = TerritoryGrid.cellOf(GeoPoint(it.latitude, it.longitude))
        cell.row / MY_LAND_CLUSTER_CELLS to cell.col / MY_LAND_CLUSTER_CELLS
    }
    val largest = groups.values.maxBy { it.size }
    return RoutePoint(largest.map { it.latitude }.average(), largest.map { it.longitude }.average())
}

/** 가장 최근 완료 기록의 마지막 위치. 현재 위치를 받지 못했을 때 지도를 열 위치로 쓴다. */
fun lastRecordedPoint(summaries: List<CompletedDrawingSummary>): RoutePoint? =
    summaries.firstNotNullOfOrNull { summary ->
        summary.thumbnail.lastOrNull()?.let { RoutePoint(it.latitude, it.longitude) }
    }

private fun List<kr.roadcanvas.app.drawing.RoutePoint>.center(): RoutePoint? = if (isEmpty()) {
    null
} else {
    RoutePoint(map { it.latitude }.average(), map { it.longitude }.average())
}
