package kr.roadcanvas.app.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.fake.fakeDrawingStorage
import kr.roadcanvas.app.map.naver.NaverRouteMapComponent
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.IntegrationSource
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection
import kr.roadcanvas.app.vehicle.fake.FakeCarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppContainerTest {
    @Test
    fun `디버그 기본값은 모의 위치와 AAOS 주행 규제를 쓴다`() = runTest {
        val container = DefaultAppContainer(
            VehicleIntegrationSelection.parse("simulated", "aaos", "stationary"),
            fakeDrawingStorage()
        )

        assertEquals(IntegrationSource.SIMULATED, container.vehicleIntegrationInfo.location)
        assertEquals(IntegrationSource.AAOS, container.vehicleIntegrationInfo.driving)
        assertEquals(
            emptyList<IntegrationSource>(),
            container.vehicleIntegrationInfo.unavailableRequests
        )
        assertEquals(
            DrivingRestriction.UNKNOWN,
            container.drivingRestrictionProvider.restriction.value
        )
        val first = container.locationProvider.locations().first()
        assertEquals(SimulatedRouteConfig.DEFAULT_CENTER.latitude, first.latitude, 0.0)
        assertEquals(SimulatedRouteConfig.DEFAULT_CENTER.longitude, first.longitude, 0.0)
    }

    @Test
    fun `릴리스 기본값은 위치만 실제 구현 전까지 모의 구현으로 대체한다`() {
        val container = DefaultAppContainer(
            VehicleIntegrationSelection.parse("pleos", "aaos", "circle"),
            fakeDrawingStorage()
        )

        assertEquals(
            "위치: 모의 · 주행 규제: AAOS (Pleos 미구현)",
            container.vehicleIntegrationInfo.label
        )
    }

    @Test
    fun `AAOS 주행 규제를 고르면 Car 연결 구현을 사용한다`() = runTest {
        val client = FakeCarUxRestrictionsClient()
        val container = DefaultAppContainer(
            VehicleIntegrationSelection.parse("simulated", "aaos", "circle"),
            fakeDrawingStorage(),
            carUxRestrictionsClient = { client }
        )

        val restriction = container.drivingRestrictionProvider.restriction
            .first { it != DrivingRestriction.UNKNOWN }

        assertEquals(DrivingRestriction.UNRESTRICTED, restriction)
        assertEquals(1, client.connectCount)
    }

    @Test
    fun `경로 지도는 네이버 지도 컴포넌트를 쓴다`() {
        val container = DefaultAppContainer(
            VehicleIntegrationSelection.parse("simulated", "simulated", "circle"),
            fakeDrawingStorage()
        )

        assertTrue(container.routeMapComponent is NaverRouteMapComponent)
    }
}
