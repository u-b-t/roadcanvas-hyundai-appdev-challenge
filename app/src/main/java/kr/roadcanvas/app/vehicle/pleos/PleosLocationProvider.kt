package kr.roadcanvas.app.vehicle.pleos

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationProvider
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.VehiclePermissions

/**
 * Pleos FusedLocation SDK로 차량 위치를 받는다.
 *
 * 수집을 시작할 때마다 새 SDK 인스턴스를 만들어 지원 여부와 권한을 확인한 뒤 초기화하고 리스너를 등록한다.
 * 수집을 멈추면 리스너를 해제하고 SDK를 해제한다. Application Context만 쓰므로 Activity가 없는
 * 포그라운드 서비스에서도 수집할 수 있다. 위치를 받을 수 없는 상태이면 위치 없이 수집이 끝난다.
 */
class PleosLocationProvider(
    private val createClient: () -> FusedLocationClient,
    private val createNoticeClient: (Context) -> FusedLocationClient,
    private val hasPermission: () -> Boolean,
    private val logWarning: (String, Throwable) -> Unit
) : LocationProvider {
    private val mutableAvailability = MutableStateFlow(checkAvailability(createClient()))

    override val availability: StateFlow<LocationAvailability> = mutableAvailability.asStateFlow()

    override fun locations(): Flow<LocationSample> = callbackFlow {
        val client = createClient()
        val initial = checkAvailability(client)
        mutableAvailability.value = initial
        if (initial != LocationAvailability.Available) {
            close()
            return@callbackFlow
        }

        val listener = object : FusedLocationClient.Listener {
            override fun onChanged(sample: LocationSample?) {
                sample ?: return
                mutableAvailability.value = LocationAvailability.Available
                trySend(sample)
            }

            override fun onError(exception: Exception) {
                logWarning("FusedLocation 오류", exception)
                mutableAvailability.value = availabilityOf(exception)
            }
        }

        client.initialize()
        try {
            client.register(listener)
        } catch (e: SecurityException) {
            mutableAvailability.value = PERMISSION_REQUIRED
            client.release()
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                client.unregister(listener)
            } catch (e: SecurityException) {
                logWarning("FusedLocation 리스너 해제 실패", e)
            } finally {
                client.release()
            }
        }
    }

    override fun showAccessBlockedNotice(activityContext: Context): Boolean = try {
        createNoticeClient(activityContext).showLocationAccessBlockedPopup()
        true
    } catch (e: RuntimeException) {
        logWarning("위치 접근 차단 안내를 띄우지 못했습니다.", e)
        false
    }

    private fun checkAvailability(client: FusedLocationClient): LocationAvailability = when {
        !client.isSupported() -> LocationAvailability.Unsupported
        !hasPermission() -> PERMISSION_REQUIRED
        else -> LocationAvailability.Available
    }

    companion object {
        private const val TAG = "PleosLocation"

        private val PERMISSION_REQUIRED =
            LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION))

        /** SDK 오류를 앱의 위치 상태로 바꾼다. */
        fun availabilityOf(exception: Exception): LocationAvailability = when {
            exception is SecurityException -> PERMISSION_REQUIRED
            FusedLocationErrors.isAccessBlocked(exception) -> LocationAvailability.Blocked
            else -> LocationAvailability.Error(exception.message)
        }

        fun create(context: Context): PleosLocationProvider {
            val appContext = context.applicationContext
            return PleosLocationProvider(
                createClient = { SdkFusedLocationClient(appContext) },
                createNoticeClient = ::SdkFusedLocationClient,
                hasPermission = {
                    appContext.checkSelfPermission(VehiclePermissions.FUSED_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                },
                logWarning = { message, error -> Log.w(TAG, message, error) }
            )
        }
    }
}
