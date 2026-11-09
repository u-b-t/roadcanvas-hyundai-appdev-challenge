package kr.roadcanvas.app.artwork

import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint

/** 공개 테스트에 쓰는 완료 기록 */
fun freeDrawing(
    id: Long = 1L,
    name: String? = "남산 하트",
    pointCount: Int = 3,
    mode: DrawingMode = DrawingMode.FREE_DRAWING
) = CompletedDrawing(
    id = id,
    mode = mode,
    completedAtMillis = 1_700_000_100_000,
    path = List(pointCount) { RoutePoint(37.5665 + it * 0.0001, 126.978, 1_700_000_000_000 + it) },
    score = 0,
    metrics = emptyMap(),
    distanceMeters = 123.4,
    durationMillis = 60_000,
    name = name
)
