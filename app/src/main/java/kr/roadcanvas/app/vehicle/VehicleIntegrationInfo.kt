package kr.roadcanvas.app.vehicle

/**
 * 실제로 사용 중인 차량 연동 구현.
 * [unavailableRequests]는 선택했지만 아직 구현되지 않아 모의 구현으로 대체한 구현이다.
 */
data class VehicleIntegrationInfo(
    val location: IntegrationSource,
    val driving: IntegrationSource,
    val unavailableRequests: List<IntegrationSource> = emptyList()
) {
    val label: String
        get() = buildString {
            append("위치: ${location.label} · 주행 규제: ${driving.label}")
            if (unavailableRequests.isNotEmpty()) {
                append(" (${unavailableRequests.joinToString { it.label }} 미구현)")
            }
        }
}
