package kr.roadcanvas.app.vehicle.fake

import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.artwork.ArtworkPublisher
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.artwork.fake.FakeArtworkStore
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.map.fake.FakeRouteMapComponent
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.fake.FakeRecordingRunner
import kr.roadcanvas.app.vehicle.IntegrationSource
import kr.roadcanvas.app.vehicle.VehicleIntegrationInfo

/** 테스트용 대체 구현을 담은 컨테이너 */
class FakeAppContainer : AppContainer {
    override val accountRegistration = AccountRegistration { AnonymousAccount("test", "드라이버-test") }
    override val locationProvider = FakeLocationProvider()
    override val drivingRestrictionProvider = FakeDrivingRestrictionProvider()
    override val vehicleIntegrationInfo =
        VehicleIntegrationInfo(location = IntegrationSource.TEST, driving = IntegrationSource.TEST)
    override val completedDrawingRepository = FakeCompletedDrawingRepository()
    override val inProgressDrawingRepository = FakeInProgressDrawingRepository()
    override val routeMapComponent = FakeRouteMapComponent()

    /** 기록 세션은 메모리에 상태를 가지므로 [reset]할 때 새로 만든다. */
    override var challengeRecorder = newRecorder()
        private set
    override val recordingRunner = FakeRecordingRunner(recorder = { challengeRecorder })

    val artworkPublicationRepository = FakeArtworkPublicationRepository()
    val artworkStore = FakeArtworkStore()
    val artworkAuthorProvider = FakeArtworkAuthorProvider()

    /** 작품 ID는 만든 순서대로 artwork-1, artwork-2...이다. */
    private var artworkCount = 0
    override val artworkPublisher = ArtworkPublisher(
        artworkPublicationRepository,
        artworkStore,
        artworkAuthorProvider,
        newArtworkId = { "artwork-${++artworkCount}" }
    )

    /** 시나리오 사이에 상태가 이어지지 않도록 초기 상태로 되돌린다. */
    fun reset() {
        recordingRunner.reset()
        locationProvider.reset()
        drivingRestrictionProvider.reset()
        completedDrawingRepository.reset()
        inProgressDrawingRepository.reset()
        artworkPublicationRepository.reset()
        artworkStore.reset()
        artworkAuthorProvider.reset()
        artworkCount = 0
        challengeRecorder = newRecorder()
    }

    /**
     * 프로세스가 종료됐다가 다시 만들어진 것처럼 기록 실행과 기록 세션의 메모리 상태만 버린다.
     * 저장소는 기기 저장소처럼 그대로 남는다.
     */
    fun restartProcess() {
        recordingRunner.reset()
        challengeRecorder = newRecorder()
    }

    private fun newRecorder() = ChallengeRecorder(locationProvider, inProgressDrawingRepository)
}
