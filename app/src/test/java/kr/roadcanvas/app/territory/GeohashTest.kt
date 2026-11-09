package kr.roadcanvas.app.territory

import kr.roadcanvas.app.score.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {
    private val cityHall = GeoPoint(37.5665, 126.9780)

    @Test
    fun `알려진 좌표를 geohash로 바꾼다`() {
        assertEquals("wydm9qy", Geohash.encode(cityHall, 7))
        assertEquals("wydm9q", Geohash.encode(cityHall, 6))
        assertEquals("u4pruydqqvj", Geohash.encode(GeoPoint(57.64911, 10.40744), 11))
    }

    @Test
    fun `칸 범위는 칸 안의 좌표를 포함하고 중심은 같은 칸이다`() {
        val bounds = Geohash.bounds("wydm9qy")

        assertEquals(37.566375732421875, bounds.south, 1e-12)
        assertEquals(37.5677490234375, bounds.north, 1e-12)
        assertEquals(126.97723388671875, bounds.west, 1e-12)
        assertEquals(126.97860717773438, bounds.east, 1e-12)
        assertEquals("wydm9qy", Geohash.encode(bounds.center, 7))
        assertEquals(4, bounds.corners.size)
    }

    @Test
    fun `geohash7 칸은 서울에서 약 121m x 153m다`() {
        val area = Geohash.cellAreaSquareMeters(7, cityHall.latitude)

        assertTrue("면적 $area", area in 18_000.0..19_000.0)
        assertEquals(area, TERRITORY_CELL_AREA_SQUARE_METERS, 1e-9)
        assertEquals(400 * area, territoryAreaOf(400), 1e-6)
    }

    @Test
    fun `범위를 덮는 접두어를 구하고 제한을 넘으면 null이다`() {
        val cell = Geohash.bounds("wydm9q")
        val inside = GeoBounds(
            cell.south + 0.001,
            cell.west + 0.001,
            cell.north - 0.001,
            cell.east - 0.001
        )
        assertEquals(listOf("wydm9q"), Geohash.covering(inside, 6, limit = 1))

        val latSpan = Geohash.latitudeSpan(6)
        val lonSpan = Geohash.longitudeSpan(6)
        val twoByThree = GeoBounds(
            inside.south,
            inside.west,
            inside.south + latSpan,
            inside.west + 2 * lonSpan
        )
        val covered = Geohash.covering(twoByThree, 6, limit = 6)!!
        assertEquals(6, covered.size)
        assertEquals(6, covered.toSet().size)
        assertTrue("wydm9q" in covered)
        assertNull(Geohash.covering(twoByThree, 6, limit = 5))
    }
}
