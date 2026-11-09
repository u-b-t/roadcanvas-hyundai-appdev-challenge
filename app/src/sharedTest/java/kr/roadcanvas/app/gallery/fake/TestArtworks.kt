package kr.roadcanvas.app.gallery.fake

import kr.roadcanvas.app.artwork.artworkChunksOf
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.gallery.PublicArtwork

/** 서울시청 근처에서 동쪽으로 [count]개 점을 1초 간격으로 기록한 경로 */
fun testRoute(count: Int, startMillis: Long = 1_700_000_000_000L): List<RoutePoint> =
    List(count) { index ->
        RoutePoint(
            37.5665 + (index % 2) * 0.0002,
            126.978 + index * 0.0001,
            startMillis + index * 1_000L
        )
    }

/** 경로 점 [pointCount]개를 청크로 나눠 저장한 서버 작품 */
fun testServerArtwork(
    id: String,
    title: String = "작품 $id",
    nickname: String = "드라이버-$id",
    createdAtMillis: Long = 1_700_000_000_000L,
    pointCount: Int = 25,
    distanceMeters: Double = 1_234.0,
    durationMillis: Long = 185_000L,
    voters: Set<String> = emptySet()
): FakeServerArtwork {
    val chunks = artworkChunksOf(testRoute(pointCount), pointsPerChunk = 10)
    return FakeServerArtwork(
        artwork = PublicArtwork(
            id = id,
            title = title,
            nickname = nickname,
            distanceMeters = distanceMeters,
            durationMillis = durationMillis,
            pointChunkCount = chunks.size,
            createdAtMillis = createdAtMillis
        ),
        chunks = chunks,
        voters = voters
    )
}
