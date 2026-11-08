package kr.roadcanvas.app.drawing.fake

import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.drawing.DrawingStorage

fun fakeDrawingStorage() = DrawingStorage(
    FakeCompletedDrawingRepository(),
    FakeInProgressDrawingRepository(),
    FakeArtworkPublicationRepository()
)
