package kr.roadcanvas.app.vehicle.simulated

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider

/** 에뮬레이터 시연용으로 항상 조작 가능 상태를 준다. */
class SimulatedDrivingRestrictionProvider : DrivingRestrictionProvider {
    override val restriction: StateFlow<DrivingRestriction> =
        MutableStateFlow(DrivingRestriction.UNRESTRICTED).asStateFlow()
}
