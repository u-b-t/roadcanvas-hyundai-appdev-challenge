package kr.roadcanvas.app.drawing

import kr.roadcanvas.app.artwork.ArtworkPublicationRepository

/** 앱이 쓰는 그림 저장소 묶음 */
class DrawingStorage(
    val completed: CompletedDrawingRepository,
    val inProgress: InProgressDrawingRepository,
    val publications: ArtworkPublicationRepository
)
