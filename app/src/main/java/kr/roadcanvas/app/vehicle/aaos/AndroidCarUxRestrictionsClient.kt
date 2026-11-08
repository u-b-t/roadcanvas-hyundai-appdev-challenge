package kr.roadcanvas.app.vehicle.aaos

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * `Car.createCar`로 Car 서비스에 연결해 `CarUxRestrictionsManager`에 리스너를 등록한다.
 * 자동차용 기기가 아니거나 Car API를 쓸 수 없으면 확인 불가로 알린다.
 */
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
        context: Context,
        private val listener: CarUxRestrictionsClient.Listener
    ) : AutoCloseable {
        private var manager: CarUxRestrictionsManager? = null
        private val car: Car = Car.createCar(
            context,
            Handler(Looper.getMainLooper()),
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT
        ) { car, ready ->
            if (ready) register(car) else unregister(notify = true)
        }

        private fun register(car: Car) {
            try {
                val uxManager =
                    car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                uxManager.registerListener { restrictions ->
                    listener.onUxRestrictionsChanged(restrictions.isRequiresDistractionOptimization)
                }
                manager = uxManager
                val current = uxManager.currentCarUxRestrictions
                if (current != null) {
                    listener.onUxRestrictionsChanged(current.isRequiresDistractionOptimization)
                }
            } catch (e: Exception) {
                Log.w(TAG, "주행 규제 리스너를 등록하지 못했습니다.", e)
                unregister(notify = true)
            }
        }

        private fun unregister(notify: Boolean) {
            runCatching { manager?.unregisterListener() }
            manager = null
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
