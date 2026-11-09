package kr.roadcanvas.app.vehicle.aaos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider

/**
 * 플랫폼의 주행 상태(`CarDrivingStateManager`)와 `CarUxRestrictions`로 주행 규제 상태를 준다.
 * 주행 상태를 받으면 그것으로 판단해 D에서 멈춘 상태(IDLING)도 조작 가능으로 본다.
 * 주행 상태를 받을 수 없는 환경이면 `isRequiresDistractionOptimization`으로 판단한다.
 * 수집하는 동안에만 Car 서비스에 연결하며, 수집자가 없으면 연결을 끊고 확인 불가로 돌아간다.
 * 연결 실패, 첫 값 미수신, 예외는 모두 확인 불가로 처리한다.
 */
class CarUxRestrictionsDrivingRestrictionProvider(
    private val client: CarUxRestrictionsClient,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : DrivingRestrictionProvider {
    private data class Inputs(
        val requiresDistractionOptimization: Boolean? = null,
        val drivingState: CarDrivingState? = null
    )

    private val inputs: StateFlow<Inputs> = callbackFlow {
        var current = Inputs()
        val listener = object : CarUxRestrictionsClient.Listener {
            override fun onUxRestrictionsChanged(requiresDistractionOptimization: Boolean) {
                current =
                    current.copy(requiresDistractionOptimization = requiresDistractionOptimization)
                trySend(current)
            }

            override fun onDrivingStateChanged(drivingState: CarDrivingState) {
                current = current.copy(drivingState = drivingState)
                trySend(current)
            }

            override fun onUnavailable() {
                current = Inputs()
                trySend(current)
            }
        }
        val connection = try {
            client.connect(listener)
        } catch (e: Exception) {
            send(Inputs())
            null
        }
        awaitClose { runCatching { connection?.close() } }
    }.stateIn(scope, whileSubscribed(), Inputs())

    override val restriction: StateFlow<DrivingRestriction> = inputs
        .map { drivingRestrictionOf(it.drivingState, it.requiresDistractionOptimization) }
        .stateIn(scope, whileSubscribed(), DrivingRestriction.UNKNOWN)

    override val basis: StateFlow<String?> = inputs
        .map { basisOf(it.drivingState, it.requiresDistractionOptimization) }
        .stateIn(scope, whileSubscribed(), null)

    companion object {
        /**
         * 주행 상태가 있으면 PARKED·IDLING은 조작 가능, MOVING은 조작 제한, UNKNOWN은 확인 불가다.
         * 주행 상태가 없으면 주행 최적화 필요 여부로 판단하고, 그것도 없으면 확인 불가다.
         */
        fun drivingRestrictionOf(
            drivingState: CarDrivingState?,
            requiresDistractionOptimization: Boolean?
        ): DrivingRestriction = when (drivingState) {
            CarDrivingState.PARKED, CarDrivingState.IDLING -> DrivingRestriction.UNRESTRICTED
            CarDrivingState.MOVING -> DrivingRestriction.RESTRICTED
            CarDrivingState.UNKNOWN -> DrivingRestriction.UNKNOWN
            null -> when (requiresDistractionOptimization) {
                true -> DrivingRestriction.RESTRICTED
                false -> DrivingRestriction.UNRESTRICTED
                null -> DrivingRestriction.UNKNOWN
            }
        }

        private fun basisOf(
            drivingState: CarDrivingState?,
            requiresDistractionOptimization: Boolean?
        ) = when {
            drivingState != null -> "주행 상태 $drivingState"
            requiresDistractionOptimization != null -> "UX 규제"
            else -> null
        }

        private fun whileSubscribed() =
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0)
    }
}
