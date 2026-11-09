package kr.roadcanvas.app.gallery

import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.gallery.fake.chunksOf
import kr.roadcanvas.app.gallery.fake.pointsJsonOf
import kr.roadcanvas.app.gallery.fake.testRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json을 쓰므로 Robolectric으로 실행한다. */
@RunWith(RobolectricTestRunner::class)
class PublicArtworkRouteTest {
    private val route = testRoute(25)
    private val chunks = chunksOf(route)

    @Test
    fun `청크를 index 순서로 이어 경로를 만든다`() {
        assertEquals(route, decodeArtworkRoute(chunks.reversed(), chunks.size))
    }

    @Test
    fun `청크가 없는 작품은 빈 경로다`() {
        assertEquals(emptyList<RoutePoint>(), decodeArtworkRoute(emptyList(), 0))
    }

    @Test
    fun `빠졌거나 겹치거나 개수보다 많은 청크는 경로 오류다`() {
        assertInvalid(chunks.drop(1), chunks.size)
        assertInvalid(chunks + chunks.last(), chunks.size)
        assertInvalid(chunks, chunks.size - 1)
        assertInvalid(chunks.map { it.copy(index = it.index + 1) }, chunks.size)
    }

    @Test
    fun `범위를 벗어나거나 유한하지 않은 좌표와 음수 시각은 경로 오류다`() {
        listOf(
            RoutePoint(91.0, 127.0, 0),
            RoutePoint(37.0, -180.5, 0),
            RoutePoint(37.0, 127.0, -1)
        ).forEach { point ->
            assertInvalid(listOf(PublicArtworkChunk(0, pointsJsonOf(listOf(point)))), 1)
        }
        assertInvalid(
            listOf(
                PublicArtworkChunk(0, """[{"latitude":NaN,"longitude":127,"recordedAtMillis":0}]""")
            ),
            1
        )
    }

    @Test
    fun `기록 시각이 청크 순서대로 늘지 않으면 경로 오류다`() {
        val swapped = listOf(chunks[1].copy(index = 0), chunks[0].copy(index = 1)) + chunks.drop(2)
        assertInvalid(swapped, chunks.size)
    }

    @Test
    fun `JSON 형식이 아니거나 숫자가 문자열이면 경로 오류다`() {
        assertInvalid(listOf(PublicArtworkChunk(0, "not json")), 1)
        assertInvalid(listOf(PublicArtworkChunk(0, """[{"latitude":37}]""")), 1)
        assertInvalid(
            listOf(
                PublicArtworkChunk(
                    0,
                    """[{"latitude":"37","longitude":127,"recordedAtMillis":0}]"""
                )
            ),
            1
        )
    }

    private fun assertInvalid(chunks: List<PublicArtworkChunk>, expectedCount: Int) {
        try {
            decodeArtworkRoute(chunks, expectedCount)
            fail("경로 오류여야 한다: $chunks")
        } catch (e: GalleryStoreException) {
            assertEquals(GalleryFailure.INVALID_ROUTE, e.failure)
        }
    }
}
