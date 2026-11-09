package kr.roadcanvas.app.di

import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.account.FirebaseAccountProvider
import kr.roadcanvas.app.artwork.ArtworkAuthorProvider
import kr.roadcanvas.app.artwork.ArtworkPublisher
import kr.roadcanvas.app.artwork.ArtworkStore
import kr.roadcanvas.app.artwork.UnavailableArtworkAuthorProvider
import kr.roadcanvas.app.artwork.UnavailableArtworkStore
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.DrawingStorage
import kr.roadcanvas.app.drawing.InProgressDrawingRepository
import kr.roadcanvas.app.gallery.PublicArtworkStore
import kr.roadcanvas.app.gallery.PublicGallery
import kr.roadcanvas.app.gallery.UnavailablePublicArtworkStore
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.RouteMapComponent
import kr.roadcanvas.app.map.naver.NaverRouteMapComponent
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingRunner
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.IntegrationSource
import kr.roadcanvas.app.vehicle.LocationProvider
import kr.roadcanvas.app.vehicle.VehicleIntegrationInfo
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection
import kr.roadcanvas.app.vehicle.aaos.CarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.aaos.CarUxRestrictionsDrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.aaos.UnavailableCarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.simulated.SimulatedDrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.simulated.SimulatedLocationProvider
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteConfig
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator

/**
 * 빌드 속성으로 고른 구현을 구성한다. 실제 구현이 없는 동안에는 모의 구현을 사용한다.
 * Pleos 위치 구현은 Context가 필요하므로 Application이 [pleosLocationProvider]로 만들어 넘긴다.
 * [carUxRestrictionsClient]는 AAOS 주행 규제를 고른 경우에만 만든다.
 * 기록 서비스도 Context가 필요하므로 [recordingRunner]로 받아 처음 쓸 때 만든다.
 * 작품 공개는 Firebase 설정이 있을 때만 Application이 [artworkStore]와 [artworkAuthorProvider]를 넘기며,
 * 기본값은 항상 실패한다. 작성자는 [accountRegistration]의 익명 계정으로 만든다.
 * 공개 작품은 Firebase 설정이 있을 때만 Application이 [publicArtworkStore]로 서버 저장소를 넘긴다.
 */
class DefaultAppContainer(
    selection: VehicleIntegrationSelection,
    drawingStorage: DrawingStorage,
    routeConfig: SimulatedRouteConfig = SimulatedRouteConfig(),
    pleosLocationProvider: () -> LocationProvider = { error("Pleos 위치 구현이 주어지지 않았습니다.") },
    carUxRestrictionsClient: () -> CarUxRestrictionsClient = { UnavailableCarUxRestrictionsClient },
    mapAuth: MapAuth = MapAuth(clientId = ""),
    recordingRunner: () -> RecordingRunner = { error("기록 실행기가 주어지지 않았습니다.") },
    onRecordingStorageFailure: (Throwable) -> Unit = {},
    artworkStore: ArtworkStore = UnavailableArtworkStore,
    artworkAuthorProvider: (AccountRegistration) -> ArtworkAuthorProvider = {
        UnavailableArtworkAuthorProvider
    },
    onArtworkFailure: (Throwable) -> Unit = {},
    publicArtworkStore: PublicArtworkStore = UnavailablePublicArtworkStore,
    onGalleryFailure: (Throwable) -> Unit = {}
) : AppContainer {
    override val accountRegistration = AccountRegistration(FirebaseAccountProvider())

    override val vehicleIntegrationInfo: VehicleIntegrationInfo = selection.resolve()

    override val locationProvider: LocationProvider =
        when (vehicleIntegrationInfo.location) {
            IntegrationSource.SIMULATED -> SimulatedLocationProvider(
                route = SimulatedRouteGenerator.generate(selection.simulatedRoute, routeConfig),
                intervalMillis = routeConfig.intervalMillis
            )
            IntegrationSource.PLEOS -> pleosLocationProvider()
            else -> error("${vehicleIntegrationInfo.location.key} 위치 구현이 없습니다.")
        }

    override val drivingRestrictionProvider: DrivingRestrictionProvider =
        when (vehicleIntegrationInfo.driving) {
            IntegrationSource.SIMULATED -> SimulatedDrivingRestrictionProvider()
            IntegrationSource.AAOS ->
                CarUxRestrictionsDrivingRestrictionProvider(carUxRestrictionsClient())
            else -> error("${vehicleIntegrationInfo.driving.key} 주행 규제 구현이 없습니다.")
        }

    override val completedDrawingRepository: CompletedDrawingRepository = drawingStorage.completed

    override val inProgressDrawingRepository: InProgressDrawingRepository =
        drawingStorage.inProgress

    override val routeMapComponent: RouteMapComponent = NaverRouteMapComponent(mapAuth)

    override val challengeRecorder: ChallengeRecorder = ChallengeRecorder(
        locationProvider,
        inProgressDrawingRepository,
        onStorageFailure = onRecordingStorageFailure
    )

    override val recordingRunner: RecordingRunner by lazy(recordingRunner)

    override val artworkPublisher = ArtworkPublisher(
        drawingStorage.publications,
        artworkStore,
        artworkAuthorProvider(accountRegistration),
        onFailure = onArtworkFailure
    )

    override val publicGallery = PublicGallery(
        publicArtworkStore,
        currentUid = { (accountRegistration.state.value as? AccountState.Ready)?.account?.uid },
        onFailure = onGalleryFailure
    )
}
