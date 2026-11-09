package kr.roadcanvas.app.vehicle.fake

import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.artwork.ArtworkPublisher
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.artwork.fake.FakeArtworkStore
import kr.roadcanvas.app.circleranking.CircleRanking
import kr.roadcanvas.app.circleranking.fake.FakeCircleRankingStore
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.gallery.PublicGallery
import kr.roadcanvas.app.gallery.fake.FakePublicArtworkStore
import kr.roadcanvas.app.map.fake.FakeRouteMapComponent
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.fake.FakeRecordingRunner
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import kr.roadcanvas.app.vehicle.IntegrationSource
import kr.roadcanvas.app.vehicle.VehicleIntegrationInfo
import kr.roadcanvas.app.voice.fake.FakeVoiceGuide

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

    /** 공개 작품 서버 저장소와 투표하는 사용자 UID. UID가 null이면 인증 전이다. */
    val publicArtworkStore = FakePublicArtworkStore()
    var voterUid: String? = DEFAULT_VOTER_UID
    override val publicGallery = PublicGallery(publicArtworkStore, currentUid = { voterUid })

    /**
     * 서버 땅따먹기. 획득 ID는 만든 순서대로 capture-1, capture-2...이고, 시각은 [territoryNowMillis]다.
     * 경합 재실행은 기다리지 않는다.
     */
    val territoryStore = FakeTerritoryStore()
    val territoryCaptureRepository = FakeTerritoryCaptureRepository()
    val territoryOwnerProvider = FakeTerritoryOwnerProvider()
    var territoryNowMillis: Long = DEFAULT_TERRITORY_NOW_MILLIS
    private var captureCount = 0
    override val territoryGame = TerritoryGame(
        territoryStore,
        territoryCaptureRepository,
        territoryOwnerProvider,
        clock = { territoryNowMillis },
        newCaptureId = { "capture-${++captureCount}" },
        conflictRetryDelayMillis = 0
    )

    override val voiceGuide = FakeVoiceGuide()

    /** 동그라미 순위 서버 저장소. 순위를 읽는 사용자는 [voterUid]와 같은 인증 사용자다. */
    val circleRankingStore = FakeCircleRankingStore()
    override val circleRanking = CircleRanking(circleRankingStore, currentUid = { voterUid })

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
        publicArtworkStore.reset()
        voterUid = DEFAULT_VOTER_UID
        territoryStore.reset()
        territoryCaptureRepository.reset()
        territoryOwnerProvider.reset()
        territoryNowMillis = DEFAULT_TERRITORY_NOW_MILLIS
        captureCount = 0
        voiceGuide.reset()
        circleRankingStore.reset()
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

    companion object {
        const val DEFAULT_VOTER_UID = "test"

        /** 2026-11-09 12:00(KST) */
        const val DEFAULT_TERRITORY_NOW_MILLIS = 1_794_193_200_000L
    }
}
