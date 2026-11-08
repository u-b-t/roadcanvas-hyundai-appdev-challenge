package kr.roadcanvas.app.vehicle

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 차량 위치를 제공한다. 앱 코드는 실제 SDK 대신 이 인터페이스에만 의존한다. */
interface LocationProvider {
    val availability: StateFlow<LocationAvailability>

    /**
     * 수집하는 동안 위치를 받는다. 수집을 멈추면 위치 수신도 멈춘다.
     * 수집을 시작하기 전에 받은 위치(마지막으로 알려진 위치 등)는 다시 내보내지 않는다.
     */
    fun locations(): Flow<LocationSample>

    /**
     * 위치 접근이 [LocationAvailability.Blocked]일 때 차량의 차단 안내를 띄운다.
     * 안내 화면을 여는 데 쓰므로 [activityContext]는 Activity여야 한다.
     * 안내를 띄우지 못했거나 지원하지 않는 구현이면 false를 돌려준다.
     */
    fun showAccessBlockedNotice(activityContext: Context): Boolean = false
}
