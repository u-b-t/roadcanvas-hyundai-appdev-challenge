package kr.roadcanvas.app.vehicle

import kotlinx.coroutines.flow.StateFlow

/** 주행 규제 상태를 제공한다. */
interface DrivingRestrictionProvider {
    val restriction: StateFlow<DrivingRestriction>
}
