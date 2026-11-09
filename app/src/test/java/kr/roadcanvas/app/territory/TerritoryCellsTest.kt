package kr.roadcanvas.app.territory

import kr.roadcanvas.app.score.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryCellsTest {
    private val latSpan = Geohash.latitudeSpan(TERRITORY_CELL_PRECISION)
    private val lonSpan = Geohash.longitudeSpan(TERRITORY_CELL_PRECISION)
    private val base = Geohash.bounds("wydm9qy")

    /** 기준 칸 서남쪽 모서리에서 칸 단위로 잰 직사각형. 경계가 칸 중심을 지나지 않도록 조금 줄이고 늘린다. */
    private fun rectangle(rows: Double, cols: Double, rowOffset: Double = 0.0) = listOf(
        GeoPoint(base.south + rowOffset * latSpan, base.west),
        GeoPoint(base.south + rowOffset * latSpan, base.west + cols * lonSpan),
        GeoPoint(base.south + (rowOffset + rows) * latSpan, base.west + cols * lonSpan),
        GeoPoint(base.south + (rowOffset + rows) * latSpan, base.west)
    )

    private fun cells(boundary: List<GeoPoint>) =
        (territoryCellsOf(boundary) as TerritoryCellsResult.Cells).cells

    @Test
    fun `중심이 다각형 안에 들어오는 칸만 획득한다`() {
        val result = cells(rectangle(rows = 3.0, cols = 4.0))

        assertEquals(12, result.size)
        assertEquals(result.sorted(), result)
        assertTrue("wydm9qy" in result)
        result.forEach { assertEquals(7, it.length) }
    }

    @Test
    fun `칸 절반을 넘지 않게 걸친 칸은 획득하지 않는다`() {
        val result = cells(rectangle(rows = 2.4, cols = 2.4))

        assertEquals(4, result.size)
    }

    @Test
    fun `삼각형은 중심이 안에 있는 칸만 획득한다`() {
        val triangle = listOf(
            GeoPoint(base.south, base.west),
            GeoPoint(base.south, base.west + 4 * lonSpan),
            GeoPoint(base.south + 4 * latSpan, base.west)
        )

        // 중심 (r+0.5, c+0.5)가 r + c + 1 < 4인 칸: 대각선 아래 6칸
        assertEquals(6, cells(triangle).size)
    }

    @Test
    fun `중심이 들어오는 칸이 없으면 무게중심이 속한 칸 하나를 획득한다`() {
        // 칸 경계에 걸친 작은 사각형. 어느 칸의 중심도 포함하지 않는다.
        val small = listOf(
            GeoPoint(base.north - 0.1 * latSpan, base.east - 0.1 * lonSpan),
            GeoPoint(base.north - 0.1 * latSpan, base.east + 0.3 * lonSpan),
            GeoPoint(base.north + 0.3 * latSpan, base.east + 0.3 * lonSpan),
            GeoPoint(base.north + 0.3 * latSpan, base.east - 0.1 * lonSpan)
        )
        val centroid = GeoPoint(base.north + 0.1 * latSpan, base.east + 0.1 * lonSpan)

        assertEquals(listOf(Geohash.encode(centroid, 7)), cells(small))
    }

    @Test
    fun `400칸까지는 획득하고 넘으면 너무 큰 영역이다`() {
        assertEquals(400, cells(rectangle(rows = 20.0, cols = 20.0)).size)
        assertEquals(
            TerritoryCellsResult.TooLarge(TERRITORY_MAX_CELLS_PER_CAPTURE),
            territoryCellsOf(rectangle(rows = 20.0, cols = 21.0))
        )
    }

    @Test
    fun `오목한 다각형의 오목한 부분 칸은 획득하지 않는다`() {
        // 3x3에서 가운데 위 칸을 뺀 U자
        val u = listOf(
            GeoPoint(base.south, base.west),
            GeoPoint(base.south, base.west + 3 * lonSpan),
            GeoPoint(base.south + 3 * latSpan, base.west + 3 * lonSpan),
            GeoPoint(base.south + 3 * latSpan, base.west + 2 * lonSpan),
            GeoPoint(base.south + 2 * latSpan, base.west + 2 * lonSpan),
            GeoPoint(base.south + 2 * latSpan, base.west + lonSpan),
            GeoPoint(base.south + 3 * latSpan, base.west + lonSpan),
            GeoPoint(base.south + 3 * latSpan, base.west)
        )
        val notch = Geohash.encode(
            GeoPoint(base.south + 2.5 * latSpan, base.west + 1.5 * lonSpan),
            7
        )

        val result = cells(u)
        assertEquals(8, result.size)
        assertTrue(notch !in result)
    }
}
