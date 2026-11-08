package kr.roadcanvas.app.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 차량 위치를 제공한다. 앱 코드는 실제 SDK 대신 이 인터페이스에만 의존한다. */
interface LocationProvider {
    val availability: StateFlow<LocationAvailability>

    /** 수집하는 동안 위치를 받는다. 수집을 멈추면 위치 수신도 멈춘다. */
    fun locations(): Flow<LocationSample>
}
