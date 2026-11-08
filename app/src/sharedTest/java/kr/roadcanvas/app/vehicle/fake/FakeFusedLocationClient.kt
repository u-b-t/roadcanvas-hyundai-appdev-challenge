package kr.roadcanvas.app.vehicle.fake

import java.util.concurrent.CopyOnWriteArrayList
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.pleos.FusedLocationClient

/** Pleos FusedLocation SDK 대신 쓰는 대체 구현. 호출 순서를 기록하고 SDK 콜백을 흉내 낸다. */
class FakeFusedLocationClient(
    private val supported: Boolean = true,
    private val calls: MutableList<String> = CopyOnWriteArrayList()
) : FusedLocationClient {
    private val listeners = CopyOnWriteArrayList<FusedLocationClient.Listener>()

    /** 권한이 없을 때처럼 [register]에서 [SecurityException]을 던진다. */
    var throwOnRegister = false

    var isReleased = false
        private set

    val history: List<String> get() = calls.toList()

    val listenerCount: Int get() = listeners.size

    override fun isSupported(): Boolean {
        calls += "isSupported"
        return supported
    }

    override fun initialize() {
        check(!isReleased) { "release한 인스턴스는 다시 쓰지 않는다." }
        calls += "initialize"
    }

    override fun register(listener: FusedLocationClient.Listener) {
        check(!isReleased) { "release한 인스턴스는 다시 쓰지 않는다." }
        calls += "register"
        if (throwOnRegister) throw SecurityException("need Permission")
        listeners += listener
    }

    override fun unregister(listener: FusedLocationClient.Listener) {
        calls += "unregister"
        listeners -= listener
    }

    override fun release() {
        calls += "release"
        isReleased = true
    }

    override fun showLocationAccessBlockedPopup() {
        calls += "showLocationAccessBlockedPopup"
    }

    fun sendLocation(sample: LocationSample?) {
        listeners.forEach { it.onChanged(sample) }
    }

    fun sendError(exception: Exception) {
        listeners.forEach { it.onError(exception) }
    }
}
