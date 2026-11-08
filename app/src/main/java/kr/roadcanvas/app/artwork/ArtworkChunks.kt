package kr.roadcanvas.app.artwork

import kr.roadcanvas.app.drawing.RoutePoint

/**
 * 청크 하나에 담는 경로 점 수. 점 하나가 JSON으로 약 100자이므로 청크는 약 5만 자(ASCII라 같은 바이트)다.
 * 서버 글자 수 제한(20만 자)과 Firestore 문서 크기 제한(1MiB)보다 충분히 작다.
 */
const val ARTWORK_CHUNK_POINTS = 500

/** 서버 명세의 청크 `pointsJson` 최대 글자 수 */
const val ARTWORK_CHUNK_MAX_CHARS = 200_000

/** 서버 명세의 `pointChunkCount` 최댓값 */
const val ARTWORK_MAX_CHUNKS = 10_000

/** 서버 명세의 닉네임 최대 글자 수 */
const val ARTWORK_NICKNAME_MAX_LENGTH = 30

/** 공개할 수 없는 경로 */
class InvalidArtworkRouteException(message: String) : IllegalArgumentException(message)

/**
 * [path]를 기록 순서대로 [pointsPerChunk]개씩 나눠 0부터 이어지는 index의 청크로 만든다.
 * 서버 규칙이 좌표와 순서를 검증하지 않으므로 유한한 위경도 범위와 0 이상의 기록 시각을 여기서 확인한다.
 * 공개할 수 없는 경로면 [InvalidArtworkRouteException]을 던진다.
 */
fun artworkChunksOf(
    path: List<RoutePoint>,
    pointsPerChunk: Int = ARTWORK_CHUNK_POINTS
): List<ArtworkChunk> {
    require(pointsPerChunk > 0)
    path.forEachIndexed { index, point ->
        if (!point.isValid()) throw InvalidArtworkRouteException("${index}번째 좌표가 올바르지 않습니다.")
    }
    val chunks = path.chunked(pointsPerChunk).mapIndexed { index, points ->
        ArtworkChunk(index, pointsJsonOf(points))
    }
    if (chunks.size > ARTWORK_MAX_CHUNKS) {
        throw InvalidArtworkRouteException("청크가 ${ARTWORK_MAX_CHUNKS}개를 넘습니다.")
    }
    chunks.firstOrNull { it.pointsJson.length > ARTWORK_CHUNK_MAX_CHARS }?.let {
        throw InvalidArtworkRouteException("${it.index}번 청크가 ${ARTWORK_CHUNK_MAX_CHARS}자를 넘습니다.")
    }
    return chunks
}

/** 서버 명세 형식 `[{"latitude":..,"longitude":..,"recordedAtMillis":..}]`의 JSON 문자열 */
fun pointsJsonOf(points: List<RoutePoint>): String = points.joinToString(
    separator = ",",
    prefix = "[",
    postfix = "]"
) {
    """{"latitude":${it.latitude},"longitude":${it.longitude},"recordedAtMillis":${it.timeMillis}}"""
}

/** 저장된 청크 index가 0부터 [expectedCount]-1까지 빠짐없이 한 번씩 있는지 */
fun chunksComplete(indices: List<Int>, expectedCount: Int): Boolean =
    indices.sorted() == (0 until expectedCount).toList()

private fun RoutePoint.isValid() = latitude.isFinite() &&
    longitude.isFinite() &&
    latitude in -90.0..90.0 &&
    longitude in -180.0..180.0 &&
    timeMillis >= 0
