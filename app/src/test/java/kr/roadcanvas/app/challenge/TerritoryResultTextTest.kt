package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.territory.TerritoryLoss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerritoryResultTextTest {
    @Test
    fun `1km² 미만 면적은 m² 단위 정수에 천 단위 쉼표를 넣는다`() {
        assertEquals("0 m²", formatArea(0.0))
        assertEquals("0 m²", formatArea(-3.0))
        assertEquals("7,854 m²", formatArea(7_853.98))
        assertEquals("999,999 m²", formatArea(999_999.4))
    }

    @Test
    fun `1km² 이상 면적은 km² 단위 소수 둘째 자리까지 보여준다`() {
        assertEquals("1.00 km²", formatArea(999_999.6))
        assertEquals("2.35 km²", formatArea(2_345_678.0))
    }

    @Test
    fun `차지하지 못한 이유는 판정 결과의 기준값으로 안내한다`() {
        assertEquals(
            "출발점에서 55 m 떨어진 곳에서 끝나 영역이 닫히지 않았어요. 출발점 50 m 안으로 돌아와 끝내 주세요",
            territoryFailureText(
                TerritoryResult.NotClosed(gapMeters = 55.2, toleranceMeters = 50.0)
            )
        )
        assertEquals(
            "둘러싼 면적 2,827 m²이 최소 5,000 m²보다 작아요",
            territoryFailureText(TerritoryResult.TooSmall(2_827.4, 5_000.0))
        )
        assertEquals(
            "기록한 위치가 부족해 영역을 판정할 수 없어요",
            territoryFailureText(TerritoryResult.InsufficientPoints)
        )
    }

    @Test
    fun `차지했으면 이유가 없다`() {
        assertNull(territoryFailureText(TerritoryResult.Claimed(6_000.0, emptyList(), 300.0)))
    }

    @Test
    fun `빼앗은 상대를 칸 수와 함께 안내하고 빼앗은 칸이 없으면 안내하지 않는다`() {
        assertEquals(
            "드라이버-a님에게서 3칸, 드라이버-b님에게서 1칸을 빼앗았어요",
            territoryLossText(
                listOf(
                    TerritoryLoss("a", "드라이버-a", listOf("c1", "c2", "c3")),
                    TerritoryLoss("b", "드라이버-b", listOf("c4"))
                )
            )
        )
        assertNull(territoryLossText(emptyList()))
    }
}
