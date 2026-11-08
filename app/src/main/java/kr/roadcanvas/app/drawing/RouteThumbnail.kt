package kr.roadcanvas.app.drawing

/** 목록 썸네일에 쓰는 축약 경로의 최대 점 수. 마지막 점을 더해 이보다 하나 많을 수 있다. */
const val ROUTE_THUMBNAIL_MAX_POINTS = 64

/**
 * 목록에서 경로 모양을 그리도록 [path]를 일정 간격으로 줄인 축약 경로.
 * 첫 점부터 `ceil(점 수 / [ROUTE_THUMBNAIL_MAX_POINTS])` 간격으로 고르고 마지막 점을 항상 넣는다.
 * DB 마이그레이션도 같은 규칙으로 기존 기록의 축약 경로를 만든다.
 */
fun routeThumbnail(path: List<RoutePoint>): List<RoutePoint> {
    if (path.size <= ROUTE_THUMBNAIL_MAX_POINTS) return path
    val step = (path.size + ROUTE_THUMBNAIL_MAX_POINTS - 1) / ROUTE_THUMBNAIL_MAX_POINTS
    val sampled = path.indices.step(step).map { path[it] }
    return if ((path.size - 1) % step == 0) sampled else sampled + path.last()
}
