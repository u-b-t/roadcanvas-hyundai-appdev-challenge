package kr.roadcanvas.app.territory

import kr.roadcanvas.app.score.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryResultCellsTest {
    @Test
    fun `반영 직전 소유자로 새 칸, 빼앗은 칸, 이미 내 칸을 나누고 많이 빼앗은 상대부터 둔다`() {
        val outcome = territoryCaptureOutcomeOf(
            uid = "me",
            previousOwners = linkedMapOf(
                "c1" to null,
                "c2" to "b",
                "c3" to "a",
                "c4" to "me",
                "c5" to "a"
            ),
            nicknameOf = { "드라이버-$it" }
        )

        assertEquals(1, outcome.newCells)
        assertEquals(3, outcome.stolenCells)
        assertEquals(1, outcome.ownCells)
        assertEquals(
            listOf(
                TerritoryLoss("a", "드라이버-a", listOf("c3", "c5")),
                TerritoryLoss("b", "드라이버-b", listOf("c2"))
            ),
            outcome.losses
        )
        assertEquals(listOf("c4"), outcome.ownCellIds)
        assertEquals(TerritoryCaptureOutcome(1, 3, 1), outcome.countsOnly())
    }

    @Test
    fun `결과 칸은 빼앗은 칸에 이전 소유자를 붙이고 주변 칸에서 획득한 칸을 뺀다`() {
        val outcome = TerritoryCaptureOutcome(
            newCells = 1,
            stolenCells = 1,
            ownCells = 1,
            losses = listOf(TerritoryLoss("a", "드라이버-a", listOf("c2"))),
            ownCellIds = listOf("c3")
        )
        val nearby = listOf(
            TerritoryCell("c2", "me", mine = true),
            TerritoryCell("n1", "a", mine = false),
            TerritoryCell("n2", "me", mine = true)
        )

        val cells = territoryResultCellsOf(listOf("c1", "c2", "c3"), outcome, nearby)

        assertEquals(
            listOf(
                ResultCell("c1", ResultCellKind.CAPTURED),
                ResultCell("c2", ResultCellKind.STOLEN, "드라이버-a"),
                ResultCell("c3", ResultCellKind.KEPT)
            ),
            cells.captured
        )
        assertEquals(listOf("n1", "n2"), cells.nearby.map { it.id })
    }

    @Test
    fun `반영 전이거나 이전 소유자를 모르면 모든 칸을 차지한 칸으로 둔다`() {
        val pending = territoryResultCellsOf(listOf("c1", "c2"), null, emptyList())
        val reloaded = territoryResultCellsOf(
            listOf("c1", "c2"),
            TerritoryCaptureOutcome(0, 2, 0),
            emptyList()
        )

        assertTrue(pending.captured.all { it.kind == ResultCellKind.CAPTURED })
        assertEquals(pending, reloaded)
    }

    @Test
    fun `주변 칸 범위는 결과 영역을 감싸는 사각형을 여백만큼 넓힌다`() {
        val boundary = listOf(
            GeoPoint(37.5660, 126.9770),
            GeoPoint(37.5660, 126.9790),
            GeoPoint(37.5670, 126.9780)
        )

        val bounds = boundsAroundArea(boundary, marginMeters = 200.0)

        // 위도 1도는 약 111.2km라 200m는 약 0.0018도다.
        assertEquals(37.5660 - 0.0018, bounds.south, 0.0001)
        assertEquals(37.5670 + 0.0018, bounds.north, 0.0001)
        assertTrue(bounds.west < 126.9770 - 0.002)
        assertTrue(bounds.east > 126.9790 + 0.002)
    }
}
