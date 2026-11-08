package kr.roadcanvas.app.navigation

import kr.roadcanvas.app.vehicle.DrivingRestriction

const val STOP_NOTICE_TITLE = "정차 후 이용할 수 있어요"
const val STOP_NOTICE_RESTRICTED = "주행 중에는 화면을 조작할 수 없어요"
const val STOP_NOTICE_UNKNOWN = "주행 상태를 확인할 수 없어 화면을 조작할 수 없어요"

/**
 * 주행 중에도 그대로 보여주는 화면.
 * 진행 화면은 경로와 측정값을 보여주고 종료·포기는 화면이 직접 막는다.
 * 시작 화면은 조작할 것이 없고, 진행 중이던 도전으로 바로 넘어가야 한다.
 */
val DRIVING_VISIBLE_ROUTES = setOf(Routes.SPLASH, Routes.CIRCLE_PROGRESS)

/**
 * [route] 화면을 정차 안내로 가릴 때 보여줄 이유. 가리지 않으면 null이다.
 * 확인할 수 없는 주행 규제도 조작 제한과 같이 가린다.
 */
fun stopNoticeReasonOf(route: String, restriction: DrivingRestriction): String? = when {
    route in DRIVING_VISIBLE_ROUTES -> null
    restriction == DrivingRestriction.RESTRICTED -> STOP_NOTICE_RESTRICTED
    restriction == DrivingRestriction.UNKNOWN -> STOP_NOTICE_UNKNOWN
    else -> null
}
