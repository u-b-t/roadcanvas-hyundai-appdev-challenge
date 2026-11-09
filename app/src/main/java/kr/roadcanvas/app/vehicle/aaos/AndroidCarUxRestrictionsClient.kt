package kr.roadcanvas.app.vehicle.aaos

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kr.roadcanvas.app.vehicle.VehiclePermissions

/**
 * `Car.createCar`로 Car 서비스에 연결해 `CarUxRestrictionsManager`와 `CarPropertyManager`의
 * 기어(`GEAR_SELECTION`)·속도(`PERF_VEHICLE_SPEED`)에 리스너를 등록한다.
 * 자동차용 기기가 아니거나 Car API를 쓸 수 없으면 확인 불가로 알린다.
 * 기어·속도를 받지 못해도 `CarUxRestrictions`는 그대로 전달한다.
 */
// 대체 API(subscribePropertyEvents 등)는 최근 Car API 버전에만 있어 Android 11~14 기기를 위해 이전 API를 쓴다.
@Suppress("DEPRECATION")
class AndroidCarUxRestrictionsClient(context: Context) : CarUxRestrictionsClient {
    private val context = context.applicationContext

    override fun connect(listener: CarUxRestrictionsClient.Listener): AutoCloseable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        ) {
            listener.onUnavailable()
            return AutoCloseable {}
        }
        return try {
            Connection(context, listener)
        } catch (e: LinkageError) {
            // android.car 라이브러리가 없는 기기
            Log.w(TAG, "Car API를 사용할 수 없습니다.", e)
            listener.onUnavailable()
            AutoCloseable {}
        }
    }

    private class Connection(
        private val context: Context,
        private val listener: CarUxRestrictionsClient.Listener
    ) : AutoCloseable {
        private var manager: CarUxRestrictionsManager? = null
        private var propertyManager: CarPropertyManager? = null
        private var speedRegistered = false
        private val car: Car = Car.createCar(
            context,
            Handler(Looper.getMainLooper()),
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT
        ) { car, ready ->
            if (ready) register(car) else unregister(notify = true)
        }

        private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val gear = (value.value as? Int)
                    ?.takeIf { value.status == CarPropertyValue.STATUS_AVAILABLE }
                    ?.let(CarGear::fromVehicleGear)
                Log.i(TAG, "gear=$gear")
                listener.onGearChanged(gear)
                // 진행 화면에서 속도 권한을 받은 뒤 기어를 바꾸면 그때 속도를 구독한다.
                registerSpeed()
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "기어 값을 읽지 못했습니다.")
                listener.onGearChanged(null)
            }
        }

        private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val speed = (value.value as? Float)
                    ?.takeIf { value.status == CarPropertyValue.STATUS_AVAILABLE }
                listener.onSpeedChanged(speed)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "속도 값을 읽지 못했습니다.")
                listener.onSpeedChanged(null)
            }
        }

        private fun register(car: Car) {
            try {
                val uxManager =
                    car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                uxManager.registerListener { restrictions ->
                    deliver(restrictions.isRequiresDistractionOptimization)
                }
                manager = uxManager
                uxManager.currentCarUxRestrictions?.let {
                    deliver(it.isRequiresDistractionOptimization)
                }
            } catch (e: Exception) {
                Log.w(TAG, "주행 규제 리스너를 등록하지 못했습니다.", e)
                unregister(notify = true)
                return
            }
            registerGear(car)
        }

        /** 기어·속도를 받지 못해도 UX 규제로 판단할 수 있으므로 실패는 로그만 남긴다. */
        private fun registerGear(car: Car) {
            try {
                val properties = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                propertyManager = properties
                registerSpeed()
                val registered = properties.registerCallback(
                    gearCallback,
                    VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE
                )
                if (!registered) Log.w(TAG, "기어 값을 구독하지 못했습니다.")
            } catch (e: Exception) {
                Log.w(TAG, "기어 값을 구독하지 못해 UX 규제로 판단합니다.", e)
            }
        }

        private fun registerSpeed() {
            val properties = propertyManager ?: return
            if (speedRegistered) return
            if (context.checkSelfPermission(VehiclePermissions.CAR_SPEED) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            speedRegistered = try {
                properties.registerCallback(
                    speedCallback,
                    VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_NORMAL
                )
            } catch (e: Exception) {
                Log.w(TAG, "속도 값을 구독하지 못했습니다.", e)
                false
            }
        }

        private fun deliver(requiresDistractionOptimization: Boolean) {
            // 플랫폼 차단 화면이 앱을 가려도 수신 값을 logcat으로 확인할 수 있게 남긴다.
            Log.i(TAG, "requiresDistractionOptimization=$requiresDistractionOptimization")
            listener.onUxRestrictionsChanged(requiresDistractionOptimization)
        }

        private fun unregister(notify: Boolean) {
            runCatching { manager?.unregisterListener() }
            manager = null
            propertyManager?.let { properties ->
                runCatching { properties.unregisterCallback(gearCallback) }
                runCatching { properties.unregisterCallback(speedCallback) }
            }
            propertyManager = null
            speedRegistered = false
            if (notify) listener.onUnavailable()
        }

        override fun close() {
            Handler(Looper.getMainLooper()).post {
                unregister(notify = false)
                runCatching { car.disconnect() }
            }
        }
    }

    private companion object {
        const val TAG = "CarUxRestrictions"
    }
}
