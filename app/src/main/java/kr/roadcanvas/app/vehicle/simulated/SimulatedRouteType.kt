package kr.roadcanvas.app.vehicle.simulated

/** 모의 위치 경로 모양. [key]는 `-Proadcanvas.simRoute` 값이다. */
enum class SimulatedRouteType(val key: String) {
    /** 원 */
    CIRCLE("circle"),

    /** 찌그러진 원 */
    SQUISHED_CIRCLE("squished"),

    /** 직선 왕복 */
    LINE("line"),

    /** 정지 */
    STATIONARY("stationary");

    companion object {
        fun fromKey(key: String): SimulatedRouteType = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException(
                "모의 경로 '$key'는 지원하지 않습니다. 사용 가능: ${entries.joinToString { it.key }}"
            )
    }
}
