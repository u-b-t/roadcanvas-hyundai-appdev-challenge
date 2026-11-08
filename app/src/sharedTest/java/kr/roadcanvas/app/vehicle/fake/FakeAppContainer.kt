package kr.roadcanvas.app.vehicle.fake

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

    /** 시나리오 사이에 상태가 이어지지 않도록 초기 상태로 되돌린다. */
    fun reset() {
        recordingRunner.reset()
        locationProvider.reset()
        drivingRestrictionProvider.reset()
        completedDrawingRepository.reset()
        inProgressDrawingRepository.reset()
        challengeRecorder = newRecorder()
    }

    private fun newRecorder() = ChallengeRecorder(locationProvider, inProgressDrawingRepository)
}
