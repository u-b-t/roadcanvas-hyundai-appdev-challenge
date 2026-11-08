package kr.roadcanvas.app.score

import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 앱의 모의 경로 생성기가 내보내는 경로도 대표 사례와 같은 범위의 점수를 받는다. */
class SimulatedRouteScoreTest {
    private val calculator = CircleScoreCalculator()

    private fun calculate(type: SimulatedRouteType) = calculator.calculate(
        SimulatedRouteGenerator.generate(type).map { GeoPoint(it.latitude, it.longitude) }
    )

    @Test
    fun `모의 원 경로는 95점 이상이다`() {
        assertTrue(calculate(SimulatedRouteType.CIRCLE).score >= 95)
    }

    @Test
    fun `찌그러진 모의 원 경로는 원보다 낮다`() {
        assertTrue(
            calculate(SimulatedRouteType.SQUISHED_CIRCLE).score <
                calculate(SimulatedRouteType.CIRCLE).score
        )
    }

    @Test
    fun `모의 직선 왕복 경로는 20점 이하다`() {
        assertTrue(calculate(SimulatedRouteType.LINE).score <= 20)
    }

    @Test
    fun `모의 정지 경로는 위치값 부족으로 0점이다`() {
        val result = calculate(SimulatedRouteType.STATIONARY)

        assertEquals(0, result.score)
        assertEquals(ZeroScoreReason.INSUFFICIENT_POINTS, result.zeroReason)
    }
}
