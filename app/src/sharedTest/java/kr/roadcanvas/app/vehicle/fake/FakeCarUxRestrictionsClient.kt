package kr.roadcanvas.app.vehicle.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roadcanvas.app.vehicle.aaos.CarUxRestrictionsClient

/** 테스트에서 Car 서비스 연결과 `CarUxRestrictions` 값을 원하는 시점에 바꾸는 대체 구현 */
class FakeCarUxRestrictionsClient : CarUxRestrictionsClient {
    /** 연결할 때 리스너에 바로 줄 현재 값. null이면 첫 값을 주지 않는다. */
    var currentRequiresDistractionOptimization: Boolean? = false

    /** 연결 시도에서 던질 예외 */
    var connectFailure: Exception? = null

    /** Car 서비스 연결 자체가 실패한 것처럼 동작한다. */
    var serviceAvailable = true

    private val mutableConnected = MutableStateFlow(false)
    private var listener: CarUxRestrictionsClient.Listener? = null

    /** 지금 연결되어 리스너가 등록된 상태인지 */
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    var connectCount = 0
        private set

    override fun connect(listener: CarUxRestrictionsClient.Listener): AutoCloseable {
        connectCount++
        connectFailure?.let { throw it }
        if (!serviceAvailable) {
            listener.onUnavailable()
            return AutoCloseable {}
        }
        this.listener = listener
        mutableConnected.value = true
        currentRequiresDistractionOptimization?.let { listener.onUxRestrictionsChanged(it) }
        return AutoCloseable {
            this.listener = null
            mutableConnected.value = false
        }
    }

    /** 플랫폼이 바뀐 주행 규제를 알린 것처럼 동작한다. */
    fun notifyChanged(requiresDistractionOptimization: Boolean) {
        currentRequiresDistractionOptimization = requiresDistractionOptimization
        listener?.onUxRestrictionsChanged(requiresDistractionOptimization)
    }

    /** Car 서비스 연결이 끊긴 것처럼 동작한다. */
    fun disconnectService() {
        listener?.onUnavailable()
    }
}
