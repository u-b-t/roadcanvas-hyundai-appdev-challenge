package kr.roadcanvas.app.territory

import kotlin.math.cos
import kotlin.math.floor
import kr.roadcanvas.app.score.GeoPoint

/** 땅따먹기 칸 문서 ID의 geohash 정밀도. 서울 기준 약 121m x 153m다. */
const val TERRITORY_CELL_PRECISION = 7

/** 지도 범위 조회에 쓰는 geohash 접두어 정밀도. 칸 문서의 `gh6` 필드와 같다. */
const val TERRITORY_QUERY_PRECISION = 6

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
private const val BITS_PER_CHAR = 5
private const val EARTH_RADIUS_METERS = 6_371_000.0

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

/** geohash 문자열 계산. 문자열은 소문자 base32다. */
object Geohash {
    /** [point]가 속한 [precision]자리 geohash */
    fun encode(point: GeoPoint, precision: Int): String {
        var south = -90.0
        var north = 90.0
        var west = -180.0
        var east = 180.0
        val hash = StringBuilder(precision)
        var bits = 0
        var value = 0
        var lonBit = true
        while (hash.length < precision) {
            if (lonBit) {
                val mid = (west + east) / 2
                value = if (point.longitude >= mid) {
                    west = mid
                    value shl 1 or 1
                } else {
                    east = mid
                    value shl 1
                }
            } else {
                val mid = (south + north) / 2
                value = if (point.latitude >= mid) {
                    south = mid
                    value shl 1 or 1
                } else {
                    north = mid
                    value shl 1
                }
            }
            lonBit = !lonBit
            if (++bits == BITS_PER_CHAR) {
                hash.append(BASE32[value])
                bits = 0
                value = 0
            }
        }
        return hash.toString()
    }

    /** [hash] 칸의 범위. 올바르지 않은 문자가 있으면 [IllegalArgumentException]을 던진다. */
    fun bounds(hash: String): GeoBounds {
        var south = -90.0
        var north = 90.0
        var west = -180.0
        var east = 180.0
        var lonBit = true
        for (char in hash) {
            val value = BASE32.indexOf(char)
            require(value >= 0) { "geohash 문자가 아닙니다: $char" }
            for (bit in BITS_PER_CHAR - 1 downTo 0) {
                val set = (value shr bit) and 1 == 1
                if (lonBit) {
                    val mid = (west + east) / 2
                    if (set) west = mid else east = mid
                } else {
                    val mid = (south + north) / 2
                    if (set) south = mid else north = mid
                }
                lonBit = !lonBit
            }
        }
        return GeoBounds(south, west, north, east)
    }

    /** [precision]자리 칸 하나의 위도 폭(도) */
    fun latitudeSpan(precision: Int): Double = 180.0 / (1L shl latitudeBits(precision))

    /** [precision]자리 칸 하나의 경도 폭(도) */
    fun longitudeSpan(precision: Int): Double = 360.0 / (1L shl longitudeBits(precision))

    /** [latitude]에서 [precision]자리 칸 하나의 면적(m²). 등장방형 근사다. */
    fun cellAreaSquareMeters(precision: Int, latitude: Double): Double {
        val metersPerDegree = EARTH_RADIUS_METERS * Math.PI / 180
        return latitudeSpan(precision) * metersPerDegree *
            longitudeSpan(precision) * metersPerDegree * cos(Math.toRadians(latitude))
    }

    /**
     * [bounds]와 겹치는 [precision]자리 칸 목록. 서남쪽부터 행 단위로 돌려준다.
     * 칸 수가 [limit]를 넘으면 null이다. 날짜 변경선을 넘는 범위는 다루지 않는다.
     */
    fun covering(bounds: GeoBounds, precision: Int, limit: Int): List<String>? {
        val latSpan = latitudeSpan(precision)
        val lonSpan = longitudeSpan(precision)
        val rows = rowRange(bounds.south, bounds.north, latSpan, -90.0)
        val cols = rowRange(bounds.west, bounds.east, lonSpan, -180.0)
        if (rows.count().toLong() * cols.count() > limit) return null
        return rows.flatMap { row ->
            cols.map { col ->
                encode(
                    GeoPoint(-90.0 + (row + 0.5) * latSpan, -180.0 + (col + 0.5) * lonSpan),
                    precision
                )
            }
        }
    }

    /** [min]~[max]가 걸치는 칸 번호 범위. [origin]은 -90(위도) 또는 -180(경도)이다. */
    internal fun rowRange(min: Double, max: Double, span: Double, origin: Double): IntRange =
        floor((min - origin) / span).toInt()..floor((max - origin) / span).toInt()

    private fun longitudeBits(precision: Int) = (precision * BITS_PER_CHAR + 1) / 2

    private fun latitudeBits(precision: Int) = precision * BITS_PER_CHAR / 2
}
