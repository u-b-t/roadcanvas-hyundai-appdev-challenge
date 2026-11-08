package kr.roadcanvas.app.mydrawings

import kotlin.math.PI
import kotlin.math.cos
import kr.roadcanvas.app.drawing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteThumbnailOffsetsTest {
    private fun point(latitude: Double, longitude: Double) = RoutePoint(latitude, longitude, 0)

    @Test
    fun `북쪽은 위, 동쪽은 오른쪽에 그린다`() {
        val offsets = thumbnailOffsets(
            listOf(point(37.0, 127.0), point(37.001, 127.0), point(37.001, 127.001)),
            sizePx = 100f,
            insetPx = 10f
        )

        assertTrue(offsets[1].y < offsets[0].y)
        assertTrue(offsets[2].x > offsets[1].x)
    }

    @Test
    fun `긴 쪽을 안쪽 여백까지 채우고 가운데에 둔다`() {
        // 위도 37도에서 동서로 남북보다 두 배 긴 사각형
        val dLon = 0.002 / cos(37.0 * PI / 180)
        val offsets = thumbnailOffsets(
            listOf(point(37.0, 127.0), point(37.0, 127.0 + dLon), point(37.001, 127.0 + dLon)),
            sizePx = 100f,
            insetPx = 10f
        )

        assertEquals(10f, offsets.minOf { it.x }, 0.5f)
        assertEquals(90f, offsets.maxOf { it.x }, 0.5f)
        assertEquals(30f, offsets.minOf { it.y }, 0.5f)
        assertEquals(70f, offsets.maxOf { it.y }, 0.5f)
    }

    @Test
    fun `모두 같은 위치면 가운데 한 점이다`() {
        val offsets = thumbnailOffsets(
            listOf(point(37.0, 127.0), point(37.0, 127.0)),
            sizePx = 64f,
            insetPx = 8f
        )

        assertTrue(offsets.all { it.x == 32f && it.y == 32f })
    }

    @Test
    fun `점이 2개 이상이고 움직였을 때만 경로 모양을 그린다`() {
        assertFalse(canDrawRouteShape(emptyList()))
        assertFalse(canDrawRouteShape(listOf(point(37.0, 127.0))))
        assertFalse(canDrawRouteShape(listOf(point(37.0, 127.0), point(37.0, 127.0))))
        assertTrue(canDrawRouteShape(listOf(point(37.0, 127.0), point(37.0001, 127.0))))
    }
}
