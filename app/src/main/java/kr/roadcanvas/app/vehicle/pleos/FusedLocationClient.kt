package kr.roadcanvas.app.vehicle.pleos

import kr.roadcanvas.app.vehicle.LocationSample

/**
 * Pleos FusedLocation SDK 인스턴스 하나를 감싼다. 테스트에서 SDK를 대체할 수 있도록 SDK 타입을 드러내지 않는다.
 * SDK와 같이 [isSupported] 확인 → [initialize] → [register] → [unregister] → [release] 순서로 쓰고,
 * [release]한 인스턴스는 다시 쓰지 않는다.
 */
interface FusedLocationClient {
    fun isSupported(): Boolean

    fun initialize()

    /** 권한이 없으면 [SecurityException]을 던진다. */
    fun register(listener: Listener)

    fun unregister(listener: Listener)

    fun release()

    /** 차량의 위치 접근 차단 안내를 띄운다. Activity Context로 만든 인스턴스에서 호출해야 한다. */
    fun showLocationAccessBlockedPopup()

    interface Listener {
        /** SDK가 위치 없이 알릴 수 있으므로 [sample]은 null일 수 있다. */
        fun onChanged(sample: LocationSample?)

        fun onError(exception: Exception)
    }
}
