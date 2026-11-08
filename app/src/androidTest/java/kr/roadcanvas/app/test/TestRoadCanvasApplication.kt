package kr.roadcanvas.app.test

import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer

/** androidTest에서 실제·모의 구현 대신 테스트용 대체 구현을 주입한다. */
class TestRoadCanvasApplication : RoadCanvasApplication() {
    override fun createContainer(): AppContainer = FakeAppContainer()
}
