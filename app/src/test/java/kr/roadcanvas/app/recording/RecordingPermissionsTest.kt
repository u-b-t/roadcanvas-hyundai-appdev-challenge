package kr.roadcanvas.app.recording

import android.Manifest
import kr.roadcanvas.app.vehicle.VehiclePermissions
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPermissionsTest {
    @Test
    fun `Android 14에서는 위치 권한과 알림 권한, 차량 속도 권한을 요청한다`() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                VehiclePermissions.CAR_SPEED
            ),
            RecordingPermissions.runtime(34)
        )
    }

    @Test
    fun `Android 13에서는 알림 권한과 차량 속도 권한을 요청한다`() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS, VehiclePermissions.CAR_SPEED),
            RecordingPermissions.runtime(33)
        )
    }

    @Test
    fun `Android 12 이하에서는 차량 속도 권한만 요청한다`() {
        assertEquals(listOf(VehiclePermissions.CAR_SPEED), RecordingPermissions.runtime(32))
    }
}
