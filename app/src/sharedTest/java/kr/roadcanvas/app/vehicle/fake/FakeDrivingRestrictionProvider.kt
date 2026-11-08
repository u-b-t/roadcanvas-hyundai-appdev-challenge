package kr.roadcanvas.app.vehicle.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider

/** 테스트에서 주행 규제 상태를 원하는 시점에 바꾸는 대체 구현 */
class FakeDrivingRestrictionProvider(
    initialRestriction: DrivingRestriction = DrivingRestriction.UNRESTRICTED
) : DrivingRestrictionProvider {
    private val mutableRestriction = MutableStateFlow(initialRestriction)

    override val restriction: StateFlow<DrivingRestriction> = mutableRestriction.asStateFlow()

    fun setRestriction(restriction: DrivingRestriction) {
        mutableRestriction.value = restriction
    }

    fun reset() {
        mutableRestriction.value = DrivingRestriction.UNRESTRICTED
    }
}
