package kr.roadcanvas.app.vehicle

/** 주행 중 화면 조작 규제 상태. */
enum class DrivingRestriction {
    /** 조작 가능 */
    UNRESTRICTED,

    /** 조작 제한 */
    RESTRICTED,

    /** 확인 불가 */
    UNKNOWN;

    /** 확인할 수 없는 상태는 안전을 위해 조작할 수 없는 것으로 본다. */
    val allowsInteraction: Boolean get() = this == UNRESTRICTED
}
