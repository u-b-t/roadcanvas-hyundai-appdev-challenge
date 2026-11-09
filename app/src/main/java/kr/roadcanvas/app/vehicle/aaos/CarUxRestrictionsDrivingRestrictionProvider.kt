package kr.roadcanvas.app.vehicle.aaos

import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider

/**
 * 기어(`GEAR_SELECTION`)·속도(`PERF_VEHICLE_SPEED`)와 `CarUxRestrictions`로 주행 규제 상태를 준다.
 * P이거나 속도가 거의 0이면 D에서 멈춘 상태도 조작 가능으로 본다.
 * 기어·속도로 판단할 수 없으면 `isRequiresDistractionOptimization`으로 판단한다.
 * 수집하는 동안에만 Car 서비스에 연결하며, 수집자가 없으면 연결을 끊고 확인 불가로 돌아간다.
 * 연결 실패, 첫 값 미수신, 예외는 모두 확인 불가로 처리한다.
 */
class CarUxRestrictionsDrivingRestrictionProvider(
    private val client: CarUxRestrictionsClient,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : DrivingRestrictionProvider {
    private data class Inputs(
        val requiresDistractionOptimization: Boolean? = null,
        val gear: CarGear? = null,
        val stopped: Boolean? = null
    )

    private val inputs: StateFlow<Inputs> = callbackFlow {
        var current = Inputs()
        val listener = object : CarUxRestrictionsClient.Listener {
            override fun onUxRestrictionsChanged(requiresDistractionOptimization: Boolean) {
                update(
                    current.copy(requiresDistractionOptimization = requiresDistractionOptimization)
                )
            }

            override fun onGearChanged(gear: CarGear?) {
                update(current.copy(gear = gear))
            }

            override fun onSpeedChanged(metersPerSecond: Float?) {
                // 속도는 자주 바뀌므로 정지 여부만 남겨 같은 판단을 다시 보내지 않는다.
                update(current.copy(stopped = metersPerSecond?.let(::isStopped)))
            }

            override fun onUnavailable() {
                current = Inputs()
                trySend(current)
            }

            private fun update(next: Inputs) {
                if (next == current) return
                current = next
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
        .map { drivingRestrictionOf(it.gear, it.stopped, it.requiresDistractionOptimization) }
        .stateIn(scope, whileSubscribed(), DrivingRestriction.UNKNOWN)

    override val basis: StateFlow<String?> = inputs
        .map { basisOf(it.gear, it.stopped, it.requiresDistractionOptimization) }
        .distinctUntilChanged()
        .stateIn(scope, whileSubscribed(), null)

    companion object {
        /** 이 속도(1km/h) 미만이면 멈춘 것으로 본다. */
        const val STOPPED_SPEED_METERS_PER_SECOND = 1f / 3.6f

        /** 후진 속도는 음수로 올 수 있어 절댓값으로 비교한다. */
        fun isStopped(metersPerSecond: Float): Boolean =
            abs(metersPerSecond) < STOPPED_SPEED_METERS_PER_SECOND

        /**
         * P이면 조작 가능이다. 속도를 알면 멈췄을 때 조작 가능, 움직이면 조작 제한이다.
         * 기어가 P가 아니고 속도도 모르면 주행 최적화 필요 여부로 판단하고, 그것도 없으면 확인 불가다.
         */
        fun drivingRestrictionOf(
            gear: CarGear?,
            stopped: Boolean?,
            requiresDistractionOptimization: Boolean?
        ): DrivingRestriction = when {
            gear == CarGear.PARK -> DrivingRestriction.UNRESTRICTED
            stopped == true -> DrivingRestriction.UNRESTRICTED
            stopped == false -> DrivingRestriction.RESTRICTED
            requiresDistractionOptimization == true -> DrivingRestriction.RESTRICTED
            requiresDistractionOptimization == false -> DrivingRestriction.UNRESTRICTED
            else -> DrivingRestriction.UNKNOWN
        }

        private fun basisOf(
            gear: CarGear?,
            stopped: Boolean?,
            requiresDistractionOptimization: Boolean?
        ): String? {
            val gearLabel = gear?.label ?: "?"
            return when {
                gear == CarGear.PARK -> "기어·속도 (P)"
                stopped != null -> "기어·속도 ($gearLabel, ${if (stopped) "정지" else "이동"})"
                requiresDistractionOptimization != null -> "UX 규제"
                else -> null
            }
        }

        private fun whileSubscribed() =
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0)
    }
}
