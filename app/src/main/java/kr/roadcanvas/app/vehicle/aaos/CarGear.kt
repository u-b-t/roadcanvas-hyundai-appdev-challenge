package kr.roadcanvas.app.vehicle.aaos

/** VHAL `GEAR_SELECTION`으로 받은 기어 */
enum class CarGear(val label: String) {
    PARK("P"),
    REVERSE("R"),
    NEUTRAL("N"),
    DRIVE("D");

    companion object {
        /**
         * `android.car.VehicleGear` 값을 바꾼다. 1단 이상의 수동 기어(`GEAR_1` 이상)는 D로 본다.
         * `GEAR_UNKNOWN`과 정의되지 않은 값은 null이다.
         */
        fun fromVehicleGear(value: Int): CarGear? = when (value) {
            GEAR_NEUTRAL -> NEUTRAL
            GEAR_REVERSE -> REVERSE
            GEAR_PARK -> PARK
            GEAR_DRIVE -> DRIVE
            in MANUAL_GEARS -> DRIVE
            else -> null
        }

        // android.car.VehicleGear 상수. 단위 테스트에서 android.car 스텁 없이 쓰도록 복사한다.
        private const val GEAR_NEUTRAL = 0x1
        private const val GEAR_REVERSE = 0x2
        private const val GEAR_PARK = 0x4
        private const val GEAR_DRIVE = 0x8

        /** `GEAR_1`(0x10)부터 `GEAR_9`(0x1000)까지 */
        private val MANUAL_GEARS = (4..12).map { 1 shl it }.toSet()
    }
}
