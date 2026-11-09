package kr.roadcanvas.app.di

import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.artwork.ArtworkPublisher
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.InProgressDrawingRepository
import kr.roadcanvas.app.gallery.PublicGallery
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingRunner
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.LocationProvider
import kr.roadcanvas.app.vehicle.VehicleIntegrationInfo

/** 앱 전체에서 공유하는 의존성. Application이 만들고 화면은 여기서 의존성을 받는다. */
interface AppContainer {
    val accountRegistration: AccountRegistration
    val locationProvider: LocationProvider
    val drivingRestrictionProvider: DrivingRestrictionProvider
    val vehicleIntegrationInfo: VehicleIntegrationInfo
    val completedDrawingRepository: CompletedDrawingRepository
    val inProgressDrawingRepository: InProgressDrawingRepository
    val routeMapComponent: RouteMapComponent
    val challengeRecorder: ChallengeRecorder
    val recordingRunner: RecordingRunner
    val artworkPublisher: ArtworkPublisher
    val publicGallery: PublicGallery
    val territoryGame: TerritoryGame
}
