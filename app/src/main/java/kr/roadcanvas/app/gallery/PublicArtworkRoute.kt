package kr.roadcanvas.app.gallery

import kr.roadcanvas.app.drawing.RoutePoint
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 서버 청크를 index 순서로 이어 경로로 만든다. 서버 규칙이 청크 개수·순서와 좌표를 검증하지 않으므로 여기서 확인한다.
 * index가 0부터 [expectedCount]-1까지 한 번씩 있어야 하고, 좌표는 유한한 위경도 범위, 기록 시각은 0 이상이며
 * 앞 점보다 이르지 않아야 한다. 맞지 않으면 [GalleryFailure.INVALID_ROUTE]인 [GalleryStoreException]을 던진다.
 */
fun decodeArtworkRoute(chunks: List<PublicArtworkChunk>, expectedCount: Int): List<RoutePoint> {
    val ordered = chunks.sortedBy { it.index }
    if (ordered.map { it.index } != (0 until expectedCount).toList()) {
        invalid("경로 청크 ${ordered.map { it.index }}가 ${expectedCount}개와 맞지 않습니다.")
    }
    val points = ordered.flatMap { chunk -> parsePoints(chunk) }
    points.forEachIndexed { index, point ->
        if (!point.isValid()) invalid("${index}번째 좌표가 올바르지 않습니다.")
        if (index > 0 && point.timeMillis < points[index - 1].timeMillis) {
            invalid("${index}번째 좌표의 기록 시각이 앞 좌표보다 이릅니다.")
        }
    }
    return points
}

private fun parsePoints(chunk: PublicArtworkChunk): List<RoutePoint> = try {
    val array = JSONArray(chunk.pointsJson)
    List(array.length()) { position ->
        val point = array.getJSONObject(position)
        RoutePoint(
            latitude = point.number("latitude").toDouble(),
            longitude = point.number("longitude").toDouble(),
            timeMillis = point.number("recordedAtMillis").toLong()
        )
    }
} catch (e: JSONException) {
    throw GalleryStoreException(
        GalleryFailure.INVALID_ROUTE,
        "${chunk.index}번 청크를 읽지 못했습니다.",
        e
    )
}

/** 문자열 숫자는 받지 않고 JSON 숫자만 읽는다. */
private fun JSONObject.number(name: String): Number =
    get(name) as? Number ?: throw JSONException("$name 값이 숫자가 아닙니다.")

private fun RoutePoint.isValid() = latitude.isFinite() &&
    longitude.isFinite() &&
    latitude in -90.0..90.0 &&
    longitude in -180.0..180.0 &&
    timeMillis >= 0

private fun invalid(message: String): Nothing =
    throw GalleryStoreException(GalleryFailure.INVALID_ROUTE, message)
