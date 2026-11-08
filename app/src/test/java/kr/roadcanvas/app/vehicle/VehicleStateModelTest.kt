package kr.roadcanvas.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateModelTest {
    @Test
    fun `위치는 사용 가능 상태에서만 받을 수 있다`() {
        assertTrue(LocationAvailability.Available.canReceiveLocation)
        listOf(
            LocationAvailability.Unsupported,
            LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION)),
            LocationAvailability.Blocked,
            LocationAvailability.Error("실패")
        ).forEach { assertFalse(it.canReceiveLocation) }
    }

    @Test
    fun `권한 필요 상태는 요청할 권한 이름을 가진다`() {
        val availability =
            LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION))

        assertEquals(listOf("pleos.car.permission.FUSED_LOCATION"), availability.permissions)
    }

    @Test
    fun `조작 가능 상태에서만 조작할 수 있고 확인 불가는 제한으로 본다`() {
        assertTrue(DrivingRestriction.UNRESTRICTED.allowsInteraction)
        assertFalse(DrivingRestriction.RESTRICTED.allowsInteraction)
        assertFalse(DrivingRestriction.UNKNOWN.allowsInteraction)
    }
}
