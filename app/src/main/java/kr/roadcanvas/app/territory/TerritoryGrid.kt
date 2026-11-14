package kr.roadcanvas.app.territory

import kotlin.math.cos
import kotlin.math.floor
import kr.roadcanvas.app.score.GeoPoint

/**
 * 땅따먹기 칸 하나의 위도 폭(도). 약 120m다.
 * 서버 데이터와 시드 스크립트(`firebase/demo-seed/data.mjs`)가 같은 칸 ID를 만들도록 계산식 대신 고정 값으로 둔다.
 */
const val TERRITORY_CELL_LAT_STEP = 0.00108

/** 땅따먹기 칸 하나의 경도 폭(도). 위도 37.5°의 cos로 보정해 서울에서 약 120m다. */
const val TERRITORY_CELL_LON_STEP = 0.00136

/** 지도 범위 조회 단위인 구역 한 변의 칸 수. 구역은 칸 8 x 8(서울 기준 약 960m 정사각형)이다. */
const val TERRITORY_REGION_CELLS = 8

private const val EARTH_RADIUS_METERS = 6_371_000.0
private val CELL_ID = Regex("""(\d{1,6})_(\d{1,6})""")

/** 위도·경도 범위. 남쪽 [south], 서쪽 [west], 북쪽 [north], 동쪽 [east] 경계(도)다. */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    /** 남서쪽부터 반시계 방향의 네 꼭짓점. 지도에 칸을 다각형으로 그릴 때 쓴다. */
    val corners: List<GeoPoint>
        get() = listOf(
            GeoPoint(south, west),
            GeoPoint(south, east),
            GeoPoint(north, east),
            GeoPoint(north, west)
        )
}

/**
 * 격자의 칸 하나. [row]는 위도 -90°부터, [col]은 경도 -180°부터 센 칸 번호다.
 * 문서 ID는 `"{row}_{col}"`, 속한 구역 ID는 두 번호를 [TERRITORY_REGION_CELLS]로 나눈 몫으로 만든 `"{row}_{col}"`이다.
 */
data class TerritoryGridCell(val row: Int, val col: Int) {
    val id: String get() = "${row}_$col"

    val regionId: String get() = "${row / TERRITORY_REGION_CELLS}_${col / TERRITORY_REGION_CELLS}"

    val bounds: GeoBounds
        get() = GeoBounds(
            south = -90.0 + row * TERRITORY_CELL_LAT_STEP,
            west = -180.0 + col * TERRITORY_CELL_LON_STEP,
            north = -90.0 + (row + 1) * TERRITORY_CELL_LAT_STEP,
            east = -180.0 + (col + 1) * TERRITORY_CELL_LON_STEP
        )

    val center: GeoPoint
        get() = GeoPoint(
            -90.0 + (row + 0.5) * TERRITORY_CELL_LAT_STEP,
            -180.0 + (col + 0.5) * TERRITORY_CELL_LON_STEP
        )
}

/**
 * 위도 간격을 고정하고 경도 간격을 서울 위도로 보정한 정사각형에 가까운 땅따먹기 격자.
 * 서울에서는 약 120m x 120m이고, 서울에서 멀어질수록 동서 길이가 달라진다. 날짜 변경선을 넘는 범위는 다루지 않는다.
 */
object TerritoryGrid {
    /** [point]가 속한 칸 */
    fun cellOf(point: GeoPoint): TerritoryGridCell = TerritoryGridCell(
        floor((point.latitude + 90.0) / TERRITORY_CELL_LAT_STEP).toInt(),
        floor((point.longitude + 180.0) / TERRITORY_CELL_LON_STEP).toInt()
    )

    /** 칸 문서 ID를 칸으로 바꾼다. 형식이 맞지 않으면(이전 geohash 칸 등) null이다. */
    fun parse(id: String): TerritoryGridCell? {
        val match = CELL_ID.matchEntire(id) ?: return null
        val row = match.groupValues[1].toInt()
        val col = match.groupValues[2].toInt()
        if (row > maxRow || col > maxCol) return null
        // 앞에 0을 붙인 번호처럼 같은 칸을 다른 문자열로 쓴 ID는 받지 않는다.
        return TerritoryGridCell(row, col).takeIf { it.id == id }
    }

    /** [id] 칸의 범위. 형식이 맞지 않으면 [IllegalArgumentException]을 던진다. */
    fun bounds(id: String): GeoBounds = cell(id).bounds

    /** [id] 칸이 속한 구역 ID. 형식이 맞지 않으면 [IllegalArgumentException]을 던진다. */
    fun regionOf(id: String): String = cell(id).regionId

    private fun cell(id: String) = requireNotNull(parse(id)) { "땅따먹기 칸 ID가 아닙니다: $id" }

    /** [latitude]에서 칸 하나의 면적(m²). 등장방형 근사다. */
    fun cellAreaSquareMeters(latitude: Double): Double {
        val metersPerDegree = EARTH_RADIUS_METERS * Math.PI / 180
        return TERRITORY_CELL_LAT_STEP * metersPerDegree *
            TERRITORY_CELL_LON_STEP * metersPerDegree * cos(Math.toRadians(latitude))
    }

    /** [bounds]와 겹치는 칸 번호 범위. 첫 값은 행(위도), 둘째 값은 열(경도) 범위다. */
    fun cellRanges(bounds: GeoBounds): Pair<IntRange, IntRange> {
        val southWest = cellOf(GeoPoint(bounds.south, bounds.west))
        val northEast = cellOf(GeoPoint(bounds.north, bounds.east))
        return southWest.row..northEast.row to southWest.col..northEast.col
    }

    /**
     * [bounds]와 겹치는 구역 ID 목록. 서남쪽부터 행 단위로 돌려준다.
     * 구역 수가 [limit]를 넘으면 null이다.
     */
    fun regionsCovering(bounds: GeoBounds, limit: Int): List<String>? {
        val (rows, cols) = cellRanges(bounds)
        val regionRows = rows.first / TERRITORY_REGION_CELLS..rows.last / TERRITORY_REGION_CELLS
        val regionCols = cols.first / TERRITORY_REGION_CELLS..cols.last / TERRITORY_REGION_CELLS
        if (regionRows.count().toLong() * regionCols.count() > limit) return null
        return regionRows.flatMap { row -> regionCols.map { col -> "${row}_$col" } }
    }

    private val maxRow = floor(180.0 / TERRITORY_CELL_LAT_STEP).toInt()
    private val maxCol = floor(360.0 / TERRITORY_CELL_LON_STEP).toInt()
}
