package kr.roadcanvas.app.challenge

import kotlin.math.PI
import kotlin.math.cos
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryProgressTest {
    @Test
    fun `반원을 그리면 지름으로 이어 닫은 반원 면적을 예상한다`() {
        val estimate = territoryEstimateOf(TestPaths.semicircle(60.0).toRoute())

        assertNotNull(estimate)
        assertEquals(PI * 60 * 60 / 2, estimate!!.areaSquareMeters, 100.0)
        assertTrue(estimate.cellCount!! >= 1)
    }

    @Test
    fun `닫힘 거리와 최소 면적을 넘지 않아도 지금까지의 영역을 예상한다`() {
        // 반지름 30m 원은 최소 면적(5,000m²)보다 작고, 반원은 끝점이 출발점에서 60m 떨어져 있다.
        val small = territoryEstimateOf(TestPaths.circle(30.0).toRoute())

        assertEquals(PI * 30 * 30, small!!.areaSquareMeters, 60.0)
    }

    @Test
    fun `둘러싼 영역이 없거나 위치가 부족하면 예상하지 않는다`() {
        assertNull(territoryEstimateOf(TestPaths.straightOutAndBack(200.0).toRoute()))
        assertNull(territoryEstimateOf(listOf(point(0.0, 0.0), point(0.0, 100.0))))
        assertNull(territoryEstimateOf(emptyList()))
    }

    @Test
    fun `한 번에 차지할 수 있는 칸을 넘으면 예상 칸을 400칸 초과로 보여준다`() {
        val side = 3_000.0
        val square = listOf(point(0.0, 0.0), point(side, 0.0), point(side, side), point(0.0, side))

        val estimate = territoryEstimateOf(square)

        assertNull(estimate!!.cellCount)
        assertEquals("400칸 초과", formatEstimatedCells(estimate))
        assertEquals("9.00 km²", formatEstimatedArea(estimate))
    }

    @Test
    fun `예상 영역이 없으면 대시로 보여준다`() {
        assertEquals("-", formatEstimatedArea(null))
        assertEquals("-", formatEstimatedCells(null))
    }

    @Test
    fun `출발점을 충분히 떠났다가 200m와 50m 안으로 돌아오면 접근 단계가 바뀐다`() {
        val out = listOf(point(0.0, 0.0), point(0.0, 150.0), point(0.0, 300.0))

        assertEquals(StartApproach.AWAY, startApproachOf(out))
        assertEquals(StartApproach.NEAR, startApproachOf(out + point(0.0, 199.0)))
        assertEquals(StartApproach.ARRIVING, startApproachOf(out + point(0.0, 40.0)))
        assertEquals(
            StartApproach.AWAY,
            startApproachOf(out + point(0.0, 40.0) + point(0.0, 250.0))
        )
    }

    @Test
    fun `출발 직후 출발점 근처에 있으면 접근 안내를 하지 않는다`() {
        assertEquals(StartApproach.AWAY, startApproachOf(listOf(point(0.0, 0.0))))
        assertEquals(StartApproach.AWAY, startApproachOf(listOf(point(0.0, 0.0), point(0.0, 10.0))))
        // 50m 경계를 넘나들어도 충분히(80m 이상) 떠나지 않았으면 돌아온 것으로 보지 않는다.
        val jitter = listOf(point(0.0, 0.0), point(0.0, 60.0), point(0.0, 45.0))
        assertEquals(StartApproach.AWAY, startApproachOf(jitter))
        // 100m만 나갔다 오면 50m 안내는 하지만 200m 안내는 하지 않는다.
        assertEquals(
            StartApproach.ARRIVING,
            startApproachOf(listOf(point(0.0, 0.0), point(0.0, 100.0), point(0.0, 30.0)))
        )
        assertEquals(
            StartApproach.AWAY,
            startApproachOf(listOf(point(0.0, 0.0), point(0.0, 100.0), point(0.0, 70.0)))
        )
    }

    @Test
    fun `더 가까운 단계로 들어올 때만 음성으로 안내한다`() {
        assertEquals(APPROACH_NEAR_SPEECH, approachSpeechOf(StartApproach.AWAY, StartApproach.NEAR))
        assertEquals(
            APPROACH_ARRIVING_SPEECH,
            approachSpeechOf(StartApproach.NEAR, StartApproach.ARRIVING)
        )
        assertEquals(
            APPROACH_ARRIVING_SPEECH,
            approachSpeechOf(StartApproach.AWAY, StartApproach.ARRIVING)
        )
        assertNull(approachSpeechOf(StartApproach.NEAR, StartApproach.NEAR))
        assertNull(approachSpeechOf(StartApproach.ARRIVING, StartApproach.NEAR))
        assertNull(approachSpeechOf(StartApproach.NEAR, StartApproach.AWAY))
    }

    private fun List<GeoPoint>.toRoute() = mapIndexed { index, it ->
        RoutePoint(it.latitude, it.longitude, index * 1_000L)
    }

    /** 서울시청에서 동쪽 [x]m, 북쪽 [y]m인 점 */
    private fun point(x: Double, y: Double): RoutePoint {
        val latitude = 37.5665 + Math.toDegrees(y / EARTH_RADIUS_METERS)
        val longitude = 126.978 +
            Math.toDegrees(x / (EARTH_RADIUS_METERS * cos(Math.toRadians(37.5665))))
        return RoutePoint(latitude, longitude, 0L)
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
