package kr.roadcanvas.app.vehicle

import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType

/** 빌드 속성으로 고른 차량 연동 구현. */
data class VehicleIntegrationSelection(
    val location: IntegrationSource,
    val driving: IntegrationSource,
    val simulatedRoute: SimulatedRouteType
) {
    init {
        require(location in LOCATION_SOURCES) { "위치 구현으로 ${location.key}를 사용할 수 없습니다." }
        require(driving in DRIVING_SOURCES) { "주행 규제 구현으로 ${driving.key}를 사용할 수 없습니다." }
    }

    /** 아직 구현되지 않은 구현은 모의 구현으로 대체한다. */
    fun resolve(): VehicleIntegrationInfo = VehicleIntegrationInfo(
        location = location.orSimulated(),
        driving = driving.orSimulated(),
        unavailableRequests = listOf(location, driving).filterNot { it.isImplemented }
    )

    companion object {
        val LOCATION_SOURCES = setOf(IntegrationSource.SIMULATED, IntegrationSource.PLEOS)
        val DRIVING_SOURCES = setOf(IntegrationSource.SIMULATED, IntegrationSource.AAOS)

        fun parse(location: String, driving: String, simulatedRoute: String) =
            VehicleIntegrationSelection(
                location = LOCATION_SOURCES.byKey(location, "위치"),
                driving = DRIVING_SOURCES.byKey(driving, "주행 규제"),
                simulatedRoute = SimulatedRouteType.fromKey(simulatedRoute)
            )

        private fun Set<IntegrationSource>.byKey(key: String, name: String) =
            firstOrNull { it.key == key }
                ?: throw IllegalArgumentException(
                    "$name 구현 '$key'는 지원하지 않습니다. 사용 가능: ${joinToString { it.key }}"
                )

        private fun IntegrationSource.orSimulated() =
            if (isImplemented) this else IntegrationSource.SIMULATED
    }
}
