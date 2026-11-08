package kr.roadcanvas.app.recording

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

/** 기록 서비스를 시작하기 전에 요청할 실행 중 권한 */
object RecordingPermissions {
    /**
     * Android 14부터 위치 타입 포그라운드 서비스는 Android 위치 권한이 있어야 시작할 수 있다.
     * Pleos 위치 권한은 이 조건을 채우지 않으므로 대략적인 위치 권한을 함께 요청한다.
     * 알림 권한은 기록 중 알림을 보여주는 데만 쓰며, 거절해도 기록은 한다.
     */
    // 권한 이름은 문자열 상수로 복사되며 [sdkInt]로 지원하는 버전에서만 쓴다.
    @SuppressLint("InlinedApi")
    fun runtime(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
