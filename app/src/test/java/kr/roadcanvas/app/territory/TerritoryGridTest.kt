package kr.roadcanvas.app.territory

import kotlin.math.cos
import kr.roadcanvas.app.score.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryGridTest {
    private val cityHall = GeoPoint(37.5665, 126.9780)
    private val metersPerDegree = 6_371_000.0 * Math.PI / 180

    @Test
    fun `알려진 좌표의 칸 ID와 구역 ID`() {
        val cell = TerritoryGrid.cellOf(cityHall)

        // (37.5665 + 90) / 0.00108 = 118117.1..., (126.978 + 180) / 0.00136 = 225719.1...
        assertEquals(TerritoryGridCell(118_117, 225_719), cell)
        assertEquals("118117_225719", cell.id)
        assertEquals("14764_28214", cell.regionId)
        assertEquals("14764_28214", TerritoryGrid.regionOf("118117_225719"))
    }

    @Test
    fun `칸 범위는 칸 안의 좌표를 포함하고 중심은 같은 칸이다`() {
        val cell = TerritoryGrid.cellOf(cityHall)
        val bounds = TerritoryGrid.bounds(cell.id)

        assertTrue(cityHall.latitude in bounds.south..bounds.north)
        assertTrue(cityHall.longitude in bounds.west..bounds.east)
        assertEquals(TERRITORY_CELL_LAT_STEP, bounds.north - bounds.south, 1e-9)
        assertEquals(TERRITORY_CELL_LON_STEP, bounds.east - bounds.west, 1e-9)
        assertEquals(cell, TerritoryGrid.cellOf(bounds.center))
        assertEquals(cell, TerritoryGrid.cellOf(cell.center))
        assertEquals(4, bounds.corners.size)
    }

    @Test
    fun `서울 칸은 약 120m 정사각형이다`() {
        val height = TERRITORY_CELL_LAT_STEP * metersPerDegree
        val width =
            TERRITORY_CELL_LON_STEP * metersPerDegree * cos(Math.toRadians(cityHall.latitude))

        assertEquals(120.0, height, 1.0)
        assertEquals(120.0, width, 1.0)
        assertTrue("가로 $width, 세로 $height", width / height in 0.99..1.01)
    }

    @Test
    fun `칸 면적과 칸 수 환산 면적`() {
        val area = TerritoryGrid.cellAreaSquareMeters(cityHall.latitude)

        assertTrue("면적 $area", area in 14_000.0..14_800.0)
        assertEquals(area, TERRITORY_CELL_AREA_SQUARE_METERS, 1e-9)
        assertEquals(400 * area, territoryAreaOf(400), 1e-6)
    }

    @Test
    fun `칸 ID 형식이 아니면 해석하지 않는다`() {
        assertEquals(TerritoryGridCell(118_117, 225_719), TerritoryGrid.parse("118117_225719"))
        assertNull(TerritoryGrid.parse("wydm9qy"))
        assertNull(TerritoryGrid.parse("0118117_225719"))
        assertNull(TerritoryGrid.parse("118117-225719"))
        assertNull(TerritoryGrid.parse("-1_5"))
        assertNull(TerritoryGrid.parse("999999_5"))
    }

    @Test
    fun `구역은 칸 8 x 8이다`() {
        val first = TerritoryGridCell(118_112, 225_712)
        val last = TerritoryGridCell(118_119, 225_719)

        assertEquals(first.regionId, last.regionId)
        assertTrue(first.regionId != TerritoryGridCell(118_120, 225_712).regionId)
        assertTrue(first.regionId != TerritoryGridCell(118_112, 225_720).regionId)
    }

    @Test
    fun `범위를 덮는 구역을 구하고 제한을 넘으면 null이다`() {
        val region = TerritoryGridCell(118_112, 225_712)
        val inside = GeoBounds(
            region.bounds.south + 0.0001,
            region.bounds.west + 0.0001,
            region.bounds.north + 5.5 * TERRITORY_CELL_LAT_STEP,
            region.bounds.east + 5.5 * TERRITORY_CELL_LON_STEP
        )
        assertEquals(listOf(region.regionId), TerritoryGrid.regionsCovering(inside, limit = 1))

        // 구역 2행 x 3열에 걸치는 범위
        val twoByThree = inside.copy(
            north = inside.south + TERRITORY_REGION_CELLS * TERRITORY_CELL_LAT_STEP,
            east = inside.west + 2 * TERRITORY_REGION_CELLS * TERRITORY_CELL_LON_STEP
        )
        val covered = TerritoryGrid.regionsCovering(twoByThree, limit = 6)!!
        assertEquals(
            listOf(
                "14764_28214",
                "14764_28215",
                "14764_28216",
                "14765_28214",
                "14765_28215",
                "14765_28216"
            ),
            covered
        )
        assertNull(TerritoryGrid.regionsCovering(twoByThree, limit = 5))
    }
}
