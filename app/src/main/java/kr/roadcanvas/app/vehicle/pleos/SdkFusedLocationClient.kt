package kr.roadcanvas.app.vehicle.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kr.roadcanvas.app.vehicle.LocationSample

/** Pleos FusedLocation SDK v1.1.3을 [FusedLocationClient]로 감싼다. */
class SdkFusedLocationClient(context: Context) : FusedLocationClient {
    private val fusedLocation = FusedLocation(context)
    private val sdkListeners =
        ConcurrentHashMap<FusedLocationClient.Listener, FusedLocationListener>()

    override fun isSupported(): Boolean = fusedLocation.isSupported()

    override fun initialize() = fusedLocation.initialize()

    override fun register(listener: FusedLocationClient.Listener) {
        val sdkListener = sdkListeners.getOrPut(listener) { listener.toSdkListener() }
        try {
            fusedLocation.registerFusedLocationCallback(sdkListener)
        } catch (e: SecurityException) {
            sdkListeners.remove(listener)
            throw e
        }
    }

    override fun unregister(listener: FusedLocationClient.Listener) {
        val sdkListener = sdkListeners.remove(listener) ?: return
        fusedLocation.unregisterFusedLocationCallback(sdkListener)
    }

    override fun release() = fusedLocation.release()

    override fun showLocationAccessBlockedPopup() = fusedLocation.showLocationAccessBlockedPopup()

    private fun FusedLocationClient.Listener.toSdkListener() = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            this@toSdkListener.onChanged(location?.toSample())
        }

        override fun onError(exception: Exception) {
            this@toSdkListener.onError(exception)
        }
    }

    private fun LocationData.toSample() = LocationSample(latitude, longitude, timestampMillis)
}
