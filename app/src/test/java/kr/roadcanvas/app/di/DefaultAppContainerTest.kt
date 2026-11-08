package kr.roadcanvas.app.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.fake.fakeDrawingStorage
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.IntegrationSource
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAppContainerTest {
    @Test
    fun `디버그 기본값은 모의 위치와 모의로 대체한 주행 규제를 쓴다`() = runTest {
        val container = DefaultAppContainer(
            VehicleIntegrationSelection.parse("simulated", "aaos", "stationary"),
            fakeDrawingStorage()
        )

        assertEquals(IntegrationSource.SIMULATED, container.vehicleIntegrationInfo.location)
        assertEquals(
            listOf(IntegrationSource.AAOS),
            container.vehicleIntegrationInfo.unavailableRequests
        )
        assertEquals(
            DrivingRestriction.UNRESTRICTED,
            container.drivingRestrictionProvider.restriction.value
        )
        val first = container.locationProvider.locations().first()
        assertEquals(SimulatedRouteConfig.DEFAULT_CENTER.latitude, first.latitude, 0.0)
        assertEquals(SimulatedRouteConfig.DEFAULT_CENTER.longitude, first.longitude, 0.0)
    }

    @Test
    fun `릴리스 기본값도 실제 구현 전에는 모의 구현으로 동작한다`() {
        val container = DefaultAppContainer(
            VehicleIntegrationSelection.parse("pleos", "aaos", "circle"),
            fakeDrawingStorage()
        )

        assertEquals(
            "위치: 모의 · 주행 규제: 모의 (Pleos, AAOS 미구현)",
            container.vehicleIntegrationInfo.label
        )
    }
}
