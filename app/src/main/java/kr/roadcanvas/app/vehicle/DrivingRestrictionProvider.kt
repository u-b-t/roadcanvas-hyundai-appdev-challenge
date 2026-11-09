package kr.roadcanvas.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 주행 규제 상태를 제공한다. */
interface DrivingRestrictionProvider {
    val restriction: StateFlow<DrivingRestriction>

    /** 현재 상태를 무엇으로 판단했는지 debug 화면에 보일 설명. 없으면 null이다. */
    val basis: StateFlow<String?> get() = NO_BASIS

    private companion object {
        val NO_BASIS: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    }
}
