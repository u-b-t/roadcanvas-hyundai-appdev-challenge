package kr.roadcanvas.app.vehicle

/** 차량 연동 구현의 종류. [key]는 빌드 속성 값이다. */
enum class IntegrationSource(val key: String, val label: String, val isImplemented: Boolean) {
    SIMULATED("simulated", "모의", isImplemented = true),
    PLEOS("pleos", "Pleos", isImplemented = false),
    AAOS("aaos", "AAOS", isImplemented = true),
    TEST("test", "테스트", isImplemented = true)
}
