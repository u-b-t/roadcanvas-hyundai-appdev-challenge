package kr.roadcanvas.app.vehicle.aaos

import android.car.Car
import android.util.Log
import java.lang.reflect.Proxy

/**
 * `CarDrivingStateManager`는 공개 SDK의 `android.car` 스텁에 없는 시스템 API라 리플렉션으로 구독한다.
 * 관리자를 얻지 못하거나 호출이 실패하면 주행 상태 없이 동작하도록 null을 돌려준다.
 */
internal class CarDrivingStateReflection private constructor(private val manager: Any) :
    AutoCloseable {
    override fun close() {
        runCatching { manager.javaClass.getMethod("unregisterListener").invoke(manager) }
    }

    companion object {
        private const val TAG = "CarDrivingState"
        private const val SERVICE = "drivingstate"
        private const val LISTENER_CLASS =
            "android.car.drivingstate.CarDrivingStateManager\$CarDrivingStateEventListener"

        /** 리스너를 등록하고 현재 주행 상태를 바로 전달한다. 실패하면 null이다. */
        fun register(car: Car, onChanged: (CarDrivingState) -> Unit): CarDrivingStateReflection? =
            try {
                val manager = car.getCarManager(SERVICE) ?: return null
                val listenerClass = Class.forName(
                    LISTENER_CLASS,
                    false,
                    manager.javaClass.classLoader
                )
                val proxy = Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { self, method, args ->
                    when (method.name) {
                        "onDrivingStateChanged" -> {
                            args?.firstOrNull()?.let { onChanged(stateOf(it)) }
                            null
                        }
                        "equals" -> self === args?.firstOrNull()
                        "hashCode" -> System.identityHashCode(self)
                        "toString" -> "CarDrivingStateListener"
                        else -> null
                    }
                }
                manager.javaClass.getMethod(
                    "registerListener",
                    listenerClass
                ).invoke(manager, proxy)
                val registered = CarDrivingStateReflection(manager)
                try {
                    manager.javaClass.getMethod("getCurrentCarDrivingState").invoke(manager)
                        ?.let { onChanged(stateOf(it)) }
                } catch (e: Exception) {
                    // 현재 값을 읽지 못해도 등록한 리스너로 이후 값을 받는다.
                    Log.w(TAG, "현재 주행 상태를 읽지 못했습니다.", e)
                }
                registered
            } catch (e: Exception) {
                Log.w(TAG, "주행 상태를 구독하지 못해 CarUxRestrictions로만 판단합니다.", e)
                null
            } catch (e: LinkageError) {
                Log.w(TAG, "주행 상태 API가 없어 CarUxRestrictions로만 판단합니다.", e)
                null
            }

        /** 값을 읽지 못하면 확인 불가로 본다. */
        private fun stateOf(event: Any): CarDrivingState = runCatching {
            CarDrivingState.fromEventValue(event.javaClass.getField("eventValue").getInt(event))
        }.getOrDefault(CarDrivingState.UNKNOWN)
    }
}
