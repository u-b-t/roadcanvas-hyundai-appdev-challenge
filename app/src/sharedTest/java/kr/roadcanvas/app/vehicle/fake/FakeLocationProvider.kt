package kr.roadcanvas.app.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationProvider
import kr.roadcanvas.app.vehicle.LocationSample

/** 테스트에서 위치 상태와 좌표를 원하는 시점에 바꾸는 대체 구현 */
class FakeLocationProvider(
    initialAvailability: LocationAvailability = LocationAvailability.Available
) : LocationProvider {
    private val mutableAvailability = MutableStateFlow(initialAvailability)
    private val samples = MutableSharedFlow<LocationSample>(extraBufferCapacity = 64)

    override val availability: StateFlow<LocationAvailability> = mutableAvailability.asStateFlow()

    /** 지금 위치를 받고 있는 수집자 수 */
    val subscriberCount: StateFlow<Int> = samples.subscriptionCount

    override fun locations(): Flow<LocationSample> = samples.asSharedFlow()

    fun setAvailability(availability: LocationAvailability) {
        mutableAvailability.value = availability
    }

    /** 위치를 받고 있는 수집자에게 [sample]을 보낸다. 수집 중이 아니면 버려진다. */
    suspend fun sendLocation(sample: LocationSample) {
        samples.emit(sample)
    }

    fun reset() {
        mutableAvailability.value = LocationAvailability.Available
    }
}
