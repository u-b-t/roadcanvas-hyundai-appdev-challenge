package kr.roadcanvas.app.navigation

import kr.roadcanvas.app.vehicle.DrivingRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingScreenPolicyTest {
    private val gatedRoutes = listOf(
        Routes.MODE_SELECT,
        Routes.CIRCLE_READY,
        Routes.CIRCLE_RESULT,
        Routes.MY_DRAWINGS
    )

    @Test
    fun `정차 중에는 어떤 화면도 가리지 않는다`() {
        (gatedRoutes + DRIVING_VISIBLE_ROUTES).forEach { route ->
            assertNull(route, stopNoticeReasonOf(route, DrivingRestriction.UNRESTRICTED))
        }
    }

    @Test
    fun `조작이 제한되면 진행 화면 외의 화면을 주행 중 안내로 가린다`() {
        gatedRoutes.forEach { route ->
            assertEquals(
                route,
                STOP_NOTICE_RESTRICTED,
                stopNoticeReasonOf(route, DrivingRestriction.RESTRICTED)
            )
        }
    }

    @Test
    fun `주행 규제를 확인할 수 없어도 진행 화면 외의 화면을 가린다`() {
        gatedRoutes.forEach { route ->
            assertEquals(
                route,
                STOP_NOTICE_UNKNOWN,
                stopNoticeReasonOf(route, DrivingRestriction.UNKNOWN)
            )
        }
    }

    @Test
    fun `주행 중에도 진행 화면과 시작 화면은 가리지 않는다`() {
        listOf(Routes.CIRCLE_PROGRESS, Routes.SPLASH).forEach { route ->
            DrivingRestriction.entries.forEach { restriction ->
                assertNull("$route $restriction", stopNoticeReasonOf(route, restriction))
            }
        }
    }
}
