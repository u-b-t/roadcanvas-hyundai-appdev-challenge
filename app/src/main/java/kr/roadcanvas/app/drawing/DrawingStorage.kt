package kr.roadcanvas.app.drawing

/** 앱이 쓰는 그림 저장소 묶음 */
class DrawingStorage(
    val completed: CompletedDrawingRepository,
    val inProgress: InProgressDrawingRepository
)
