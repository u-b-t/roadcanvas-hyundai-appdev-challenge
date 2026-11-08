package kr.roadcanvas.app.vehicle.simulated

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.LocationProvider
import kr.roadcanvas.app.vehicle.LocationSample

/** 수집을 시작하면 [route]를 처음부터 [intervalMillis] 간격으로 반복 재생한다. */
class SimulatedLocationProvider(
    private val route: List<GeoPoint>,
    private val intervalMillis: Long = SimulatedRouteConfig.DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) : LocationProvider {
    init {
        require(route.isNotEmpty()) { "모의 경로에 지점이 없습니다." }
        require(intervalMillis > 0) { "재생 간격은 0보다 커야 합니다." }
    }

    override val availability: StateFlow<LocationAvailability> =
        MutableStateFlow(LocationAvailability.Available).asStateFlow()

    override fun locations(): Flow<LocationSample> = flow {
        var index = 0
        while (true) {
            val point = route[index]
            emit(LocationSample(point.latitude, point.longitude, clock()))
            index = (index + 1) % route.size
            delay(intervalMillis)
        }
    }
}
