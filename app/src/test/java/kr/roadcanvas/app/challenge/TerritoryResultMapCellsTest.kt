package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.map.MapCellStyle
import kr.roadcanvas.app.territory.ResultCell
import kr.roadcanvas.app.territory.ResultCellKind
import kr.roadcanvas.app.territory.TerritoryCell
import kr.roadcanvas.app.territory.TerritoryResultCells
import org.junit.Assert.assertEquals
import org.junit.Test

class TerritoryResultMapCellsTest {
    @Test
    fun `주변 칸을 흐리게 먼저 두고 이번 칸을 나눠 그리며 이전 소유자는 한 칸에만 붙인다`() {
        val cells = mapCellsOf(
            TerritoryResultCells(
                captured = listOf(
                    ResultCell("wydm9qy", ResultCellKind.CAPTURED),
                    ResultCell("wydm9qz", ResultCellKind.STOLEN, "드라이버-a"),
                    ResultCell("wydm9qw", ResultCellKind.STOLEN, "드라이버-a"),
                    ResultCell("wydm9qx", ResultCellKind.KEPT)
                ),
                nearby = listOf(TerritoryCell("wydm9qv", "a", mine = false))
            )
        )

        assertEquals(
            listOf(
                MapCellStyle.FAINT,
                MapCellStyle.CAPTURED,
                MapCellStyle.STOLEN,
                MapCellStyle.STOLEN,
                MapCellStyle.KEPT
            ),
            cells.map { it.style }
        )
        assertEquals(listOf(false, true, true, true, true), cells.map { it.mine })
        assertEquals(listOf(null, null, "드라이버-a", null, null), cells.map { it.label })
        assertEquals(List(5) { 4 }, cells.map { it.corners.size })
    }
}
