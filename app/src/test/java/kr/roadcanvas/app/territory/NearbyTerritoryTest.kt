package kr.roadcanvas.app.territory

import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTerritoryTest {
    private val store = FakeTerritoryStore()
    private val game =
        TerritoryGame(store, FakeTerritoryCaptureRepository(), FakeTerritoryOwnerProvider())
    private val cityHall = GeoPoint(37.5665, 126.978)

    @Test
    fun `내 칸과 칸이 가장 많은 사용자를 요약한다`() {
        val cells = cells("test", 3, mine = true) + cells("u1", 5) + cells("u2", 2)
        val ranking = listOf(TerritoryRank(1, "u1", "드라이버-01", 40, mine = false))

        val summary = nearbyTerritorySummaryOf(cells, ranking)

        assertEquals(NearbyTerritorySummary(10, 3, NearbyLeader("드라이버-01", 5, false)), summary)
        assertEquals(
            "주변 칸 10개 중 내 땅 3칸, 가장 많은 사람 드라이버-01님 5칸",
            formatNearbySummary(summary)
        )
    }

    @Test
    fun `순위에 없는 사용자는 자동 닉네임으로 보여주고 칸 수가 같으면 UID 순서로 고른다`() {
        val summary =
            nearbyTerritorySummaryOf(cells("bbbbbbbbbb", 2) + cells("aaaaaaaaaa", 2), emptyList())

        assertEquals(NearbyLeader("드라이버-aaaaaaaa", 2, false), summary.leader)
    }

    @Test
    fun `내가 가장 많거나 주변에 칸이 없으면 그에 맞게 안내한다`() {
        val mine =
            nearbyTerritorySummaryOf(cells("test", 4, mine = true) + cells("u1", 1), emptyList())
        assertEquals("주변 칸 5개 중 내 땅 4칸, 내가 가장 많아요", formatNearbySummary(mine))

        val empty = nearbyTerritorySummaryOf(emptyList(), emptyList())
        assertNull(empty.leader)
        assertEquals(NEARBY_TERRITORY_EMPTY, formatNearbySummary(empty))
    }

    @Test
    fun `현재 위치에서 동서남북으로 600m 범위를 읽는다`() {
        val bounds = boundsAround(cityHall)

        assertEquals(1_200.0 / 111_195.0, bounds.north - bounds.south, 1e-5)
        assertTrue(bounds.east - bounds.west > bounds.north - bounds.south)
        assertEquals(cityHall.latitude, bounds.center.latitude, 1e-9)
    }

    @Test
    fun `서버에서 주변 칸과 순위를 읽어 요약한다`() = runTest {
        val near = TerritoryGrid.cellOf(cityHall).id
        store.putCells("u1", "드라이버-01", listOf(near))
        store.putCells("far", "드라이버-far", listOf(TerritoryGrid.cellOf(GeoPoint(37.6, 127.1)).id))

        val nearby = game.nearbyTerritory(cityHall) as NearbyTerritory.Loaded

        assertEquals(listOf(near), nearby.cells.map { it.id })
        assertEquals(NearbyLeader("드라이버-01", 1, false), nearby.summary.leader)
    }

    @Test
    fun `서버에 연결하지 못하면 실패 이유를 안내한다`() = runTest {
        store.networkFailure = true

        val nearby = game.nearbyTerritory(cityHall)

        assertEquals(NearbyTerritory.Failed(TerritoryFailure.NETWORK), nearby)
        assertEquals("$NEARBY_TERRITORY_FAILED (네트워크에 연결하지 못했어요)", nearby.text)
    }

    private fun cells(uid: String, count: Int, mine: Boolean = false) =
        List(count) { TerritoryCell("$uid-$it", uid, mine) }
}
