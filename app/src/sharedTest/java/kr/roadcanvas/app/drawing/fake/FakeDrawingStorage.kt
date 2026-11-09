package kr.roadcanvas.app.drawing.fake

import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.drawing.DrawingStorage
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository

fun fakeDrawingStorage() = DrawingStorage(
    FakeCompletedDrawingRepository(),
    FakeInProgressDrawingRepository(),
    FakeArtworkPublicationRepository(),
    FakeTerritoryCaptureRepository()
)
