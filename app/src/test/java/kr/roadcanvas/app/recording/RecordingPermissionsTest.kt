package kr.roadcanvas.app.recording

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPermissionsTest {
    @Test
    fun `Android 14에서는 위치 권한과 알림 권한을 요청한다`() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            ),
            RecordingPermissions.runtime(34)
        )
    }

    @Test
    fun `Android 13에서는 알림 권한만 요청한다`() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            RecordingPermissions.runtime(33)
        )
    }

    @Test
    fun `Android 12 이하에서는 요청할 권한이 없다`() {
        assertEquals(emptyList<String>(), RecordingPermissions.runtime(32))
    }
}
