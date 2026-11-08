package kr.roadcanvas.app.vehicle

/** 위치를 받을 수 있는 상태. SDK 타입 대신 앱이 다루는 상태로 바꿔 전달한다. */
sealed interface LocationAvailability {
    /** 위치를 받을 수 있다. */
    data object Available : LocationAvailability

    /** 차량이나 실행 환경이 위치 기능을 지원하지 않는다. */
    data object Unsupported : LocationAvailability

    /** [permissions]를 요청해 허용받아야 위치를 받을 수 있다. */
    data class PermissionRequired(val permissions: List<String>) : LocationAvailability

    /** 차량의 개인정보 보호 설정 등으로 위치 접근이 차단되었다. */
    data object Blocked : LocationAvailability

    /** 위치를 받는 중 오류가 발생했다. */
    data class Error(val message: String? = null) : LocationAvailability

    val canReceiveLocation: Boolean get() = this == Available
}
