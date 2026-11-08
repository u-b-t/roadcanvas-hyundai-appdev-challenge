package kr.roadcanvas.app.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.debug.RouteMapPreviewActivity
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.map.fake.FakeRouteMapComponent
import kr.roadcanvas.app.map.naver.NaverRouteMapComponent
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType
import org.junit.Assert.assertTrue

class RouteMapSteps {
    private val application = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()

    @Given("테스트용 지도 컴포넌트가 주입되어 있다")
    fun assertFakeMapInjected() {
        assertTrue(application.container.routeMapComponent is FakeRouteMapComponent)
    }

    @When("완료된 경로 지점 {int}개로 경로 지도를 표시한다")
    fun showCompletedRoute(count: Int) {
        showWithDrivingInfo { RouteMap(routePoints(count), it) }
    }

    @When("진행 중 경로 지점 {int}개로 경로 지도를 표시한다")
    fun showRouteInProgress(count: Int) {
        showWithDrivingInfo {
            RouteMap(routePoints(count), it, RouteMapOptions(inProgress = true))
        }
    }

    @When("지도 키 없이 네이버 경로 지도를 표시한다")
    fun showNaverMapWithoutKey() {
        val component = NaverRouteMapComponent(MapAuth(clientId = ""))
        showWithDrivingInfo { component.Content(routePoints(3), RouteMapOptions(), it) }
    }

    @When("네이버 지도 인증이 {string} 오류로 실패한 뒤 경로 지도를 표시한다")
    fun showNaverMapAfterAuthFailure(errorCode: String) {
        // 실제 인증 요청 없이 실패 상태를 먼저 만들어 지도 생성 전에 안내가 표시되는지 확인한다.
        val auth = MapAuth(clientId = "test-client-id").apply {
            reportFailure(errorCode, "테스트 인증 실패")
        }
        val component = NaverRouteMapComponent(auth)
        showWithDrivingInfo { component.Content(routePoints(3), RouteMapOptions(), it) }
    }

    @When("경로 지도 미리보기를 연다")
    fun openPreview() {
        TestScreens.launch(RouteMapPreviewActivity::class.java)
    }

    /** 지도 아래에 다른 정보가 함께 있는 화면을 띄워 지도 영역의 안내가 나머지를 가리지 않는지 확인한다. */
    private fun showWithDrivingInfo(map: @Composable (Modifier) -> Unit) {
        TestScreens.launchContent {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("주행 정보")
                map(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    private fun routePoints(count: Int) =
        SimulatedRouteGenerator.generate(SimulatedRouteType.CIRCLE)
            .take(count)
            .map { RoutePoint(it.latitude, it.longitude) }
}
