package kr.roadcanvas.app.vehicle

import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleIntegrationSelectionTest {
    @Test
    fun `빌드 속성 값으로 구현을 고른다`() {
        val selection = VehicleIntegrationSelection.parse("pleos", "aaos", "line")

        assertEquals(IntegrationSource.PLEOS, selection.location)
        assertEquals(IntegrationSource.AAOS, selection.driving)
        assertEquals(SimulatedRouteType.LINE, selection.simulatedRoute)
    }

    @Test
    fun `모의 구현을 고르면 그대로 사용한다`() {
        val info = VehicleIntegrationSelection.parse("simulated", "simulated", "circle").resolve()

        assertEquals(
            VehicleIntegrationInfo(IntegrationSource.SIMULATED, IntegrationSource.SIMULATED),
            info
        )
        assertEquals("위치: 모의 · 주행 규제: 모의", info.label)
    }

    @Test
    fun `아직 구현되지 않은 실제 구현은 모의 구현으로 대체하고 표시한다`() {
        val info = VehicleIntegrationSelection.parse("pleos", "aaos", "circle").resolve()

        assertEquals(IntegrationSource.SIMULATED, info.location)
        assertEquals(IntegrationSource.AAOS, info.driving)
        assertEquals("위치: 모의 · 주행 규제: AAOS (Pleos 미구현)", info.label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `위치 구현에 지원하지 않는 값을 쓰면 거부한다`() {
        VehicleIntegrationSelection.parse("aaos", "simulated", "circle")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `주행 규제 구현에 지원하지 않는 값을 쓰면 거부한다`() {
        VehicleIntegrationSelection.parse("simulated", "pleos", "circle")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `테스트 구현은 빌드 속성으로 고를 수 없다`() {
        VehicleIntegrationSelection(
            IntegrationSource.TEST,
            IntegrationSource.SIMULATED,
            SimulatedRouteType.CIRCLE
        )
    }
}
