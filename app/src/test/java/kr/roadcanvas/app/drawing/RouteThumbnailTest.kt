package kr.roadcanvas.app.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteThumbnailTest {
    private fun path(count: Int) = List(count) { RoutePoint(37.0 + it, 127.0, it.toLong()) }

    @Test
    fun `점이 최대 개수 이하이면 그대로 쓴다`() {
        val path = path(ROUTE_THUMBNAIL_MAX_POINTS)

        assertEquals(path, routeThumbnail(path))
        assertEquals(emptyList<RoutePoint>(), routeThumbnail(emptyList()))
    }

    @Test
    fun `긴 경로는 일정 간격으로 줄이고 첫 점과 마지막 점을 남긴다`() {
        val path = path(200)

        val thumbnail = routeThumbnail(path)

        // 간격은 ceil(200 / 64) = 4이므로 0, 4, ..., 196에 마지막 점 199를 더한다.
        assertEquals((0 until 200 step 4).toList() + 199, thumbnail.map { it.timeMillis.toInt() })
    }

    @Test
    fun `마지막 점이 간격에 맞으면 한 번만 넣는다`() {
        // 간격은 ceil(130 / 64) = 3이고 마지막 점 129가 간격에 맞는다.
        val thumbnail = routeThumbnail(path(130))

        assertEquals((0 until 130 step 3).toList(), thumbnail.map { it.timeMillis.toInt() })
    }

    @Test
    fun `아주 긴 경로도 최대 개수보다 하나 많은 점 이하로 줄인다`() {
        listOf(65, 1_000, 3_601, 36_000).forEach { count ->
            val thumbnail = routeThumbnail(path(count))

            assertTrue(
                "$count: ${thumbnail.size}",
                thumbnail.size <= ROUTE_THUMBNAIL_MAX_POINTS + 1
            )
            assertEquals(0L, thumbnail.first().timeMillis)
            assertEquals(count - 1L, thumbnail.last().timeMillis)
        }
    }
}
