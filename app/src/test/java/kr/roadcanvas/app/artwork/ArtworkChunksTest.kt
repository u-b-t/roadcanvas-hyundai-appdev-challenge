package kr.roadcanvas.app.artwork

import kr.roadcanvas.app.drawing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkChunksTest {
    private fun path(count: Int) =
        List(count) { RoutePoint(37.5 + it * 0.0001, 127.0, 1_000L + it) }

    @Test
    fun `경로를 기록 순서대로 나눠 0부터 이어지는 index를 붙인다`() {
        val chunks = artworkChunksOf(path(1_201), pointsPerChunk = 500)

        assertEquals(listOf(0, 1, 2), chunks.map { it.index })
        assertTrue(chunks[0].pointsJson.contains("\"recordedAtMillis\":1000}"))
        assertTrue(chunks[1].pointsJson.startsWith("[{\"latitude\":${37.5 + 500 * 0.0001},"))
        assertTrue(chunks[2].pointsJson.contains("\"recordedAtMillis\":2200}"))
        assertEquals(201, chunks[2].pointsJson.split("latitude").size - 1)
    }

    @Test
    fun `청크는 서버 명세의 좌표 JSON 배열 형식이다`() {
        val json = pointsJsonOf(listOf(RoutePoint(37.0, 127.0, 1_700_000_000_000)))

        assertEquals(
            """[{"latitude":37.0,"longitude":127.0,"recordedAtMillis":1700000000000}]""",
            json
        )
    }

    @Test
    fun `기본 청크는 서버 글자 수 제한보다 충분히 작다`() {
        val long = List(ARTWORK_CHUNK_POINTS) {
            RoutePoint(-37.123456789012345, -127.12345678901234, 1_700_000_000_000)
        }

        val chunk = artworkChunksOf(long).single()

        assertTrue(chunk.pointsJson.length < ARTWORK_CHUNK_MAX_CHARS / 2)
    }

    @Test
    fun `빈 경로는 청크가 없다`() {
        assertEquals(emptyList<ArtworkChunk>(), artworkChunksOf(emptyList()))
    }

    @Test(expected = InvalidArtworkRouteException::class)
    fun `유한하지 않은 좌표는 공개할 수 없다`() {
        artworkChunksOf(listOf(RoutePoint(Double.NaN, 127.0, 0)))
    }

    @Test(expected = InvalidArtworkRouteException::class)
    fun `범위를 벗어난 위도는 공개할 수 없다`() {
        artworkChunksOf(listOf(RoutePoint(91.0, 127.0, 0)))
    }

    @Test(expected = InvalidArtworkRouteException::class)
    fun `음수 기록 시각은 공개할 수 없다`() {
        artworkChunksOf(listOf(RoutePoint(37.0, 127.0, -1)))
    }

    @Test
    fun `저장된 청크가 빠짐없이 한 번씩 있어야 완성이다`() {
        assertTrue(chunksComplete(listOf(2, 0, 1), 3))
        assertTrue(chunksComplete(emptyList(), 0))
        assertFalse(chunksComplete(listOf(0, 2), 3))
        assertFalse(chunksComplete(listOf(0, 1, 2, 3), 3))
        assertFalse(chunksComplete(listOf(0, 1, 1), 3))
    }
}
