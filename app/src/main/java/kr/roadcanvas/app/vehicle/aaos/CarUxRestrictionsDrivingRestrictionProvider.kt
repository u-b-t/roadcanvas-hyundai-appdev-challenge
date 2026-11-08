package kr.roadcanvas.app.vehicle.aaos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider

/**
 * 플랫폼의 `CarUxRestrictions`로 주행 규제 상태를 준다.
 * 수집하는 동안에만 Car 서비스에 연결하며, 수집자가 없으면 연결을 끊고 확인 불가로 돌아간다.
 * 연결 실패, 첫 값 미수신, 예외는 모두 확인 불가로 처리한다.
 */
class CarUxRestrictionsDrivingRestrictionProvider(
    private val client: CarUxRestrictionsClient,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : DrivingRestrictionProvider {
    override val restriction: StateFlow<DrivingRestriction> = callbackFlow {
        val listener = object : CarUxRestrictionsClient.Listener {
            override fun onUxRestrictionsChanged(requiresDistractionOptimization: Boolean) {
                trySend(drivingRestrictionOf(requiresDistractionOptimization))
            }

            override fun onUnavailable() {
                trySend(DrivingRestriction.UNKNOWN)
            }
        }
        val connection = try {
            client.connect(listener)
        } catch (e: Exception) {
            send(DrivingRestriction.UNKNOWN)
            null
        }
        awaitClose { runCatching { connection?.close() } }
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        DrivingRestriction.UNKNOWN
    )

    companion object {
        fun drivingRestrictionOf(requiresDistractionOptimization: Boolean) =
            if (requiresDistractionOptimization) {
                DrivingRestriction.RESTRICTED
            } else {
                DrivingRestriction.UNRESTRICTED
            }
    }
}
