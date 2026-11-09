package kr.roadcanvas.app.vehicle.aaos

/** AAOS `CarDrivingStateEvent`의 주행 상태 */
enum class CarDrivingState {
    PARKED,
    IDLING,
    MOVING,
    UNKNOWN;

    companion object {
        /** `CarDrivingStateEvent.eventValue`(-1 UNKNOWN, 0 PARKED, 1 IDLING, 2 MOVING)를 바꾼다. */
        fun fromEventValue(value: Int): CarDrivingState = when (value) {
            0 -> PARKED
            1 -> IDLING
            2 -> MOVING
            else -> UNKNOWN
        }
    }
}
