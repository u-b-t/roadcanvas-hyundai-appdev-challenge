package kr.roadcanvas.app.score

import kotlin.math.PI
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryCalculatorTest {
    private val calculator = TerritoryCalculator()

    private fun claimed(points: List<GeoPoint>): TerritoryResult.Claimed {
        val result = calculator.calculate(points)
        assertTrue("$result", result is TerritoryResult.Claimed)
        return result as TerritoryResult.Claimed
    }

    private fun assertNear(expected: Double, actual: Double, ratio: Double) {
        assertEquals(expected, actual, expected * ratio)
    }

    @Test
    fun `기본값은 닫힘 허용 50m, 최소 면적 5000m²다`() {
        assertEquals(50.0, calculator.config.closureToleranceMeters, 0.0)
        assertEquals(5_000.0, calculator.config.minAreaSquareMeters, 0.0)
    }

    @Test
    fun `정사각형 1바퀴는 변의 제곱 면적과 네 변 둘레를 차지한다`() {
        val result = claimed(TestPaths.square(100.0))

        assertNear(10_000.0, result.areaSquareMeters, 0.01)
        assertNear(400.0, result.perimeterMeters, 0.01)
    }

    @Test
    fun `원 1바퀴의 면적은 원 넓이에 가깝고 경계는 첫 점을 반복하지 않는다`() {
        val path = TestPaths.circle(60.0)
        val result = claimed(path)

        assertNear(PI * 60 * 60, result.areaSquareMeters, 0.01)
        assertNear(2 * PI * 60, result.perimeterMeters, 0.01)
        assertTrue(result.boundary.first() != result.boundary.last())
        assertEquals(path.first().latitude, result.boundary.first().latitude, 1e-9)
        assertEquals(path.first().longitude, result.boundary.first().longitude, 1e-9)
    }

    @Test
    fun `시계 방향으로 돌아도 면적은 양수로 같다`() {
        val counterClockwise = claimed(TestPaths.circle(60.0)).areaSquareMeters
        val clockwise = claimed(TestPaths.circle(60.0, clockwise = true)).areaSquareMeters

        assertEquals(counterClockwise, clockwise, 1e-6)
    }

    @Test
    fun `끝점이 허용 거리 안이면 닫힌 경로로 보고 끝점에서 시작점까지 이어 계산한다`() {
        val result = calculator.calculate(TestPaths.openCircle(60.0, gapMeters = 45.0))

        assertTrue("$result", result is TerritoryResult.Claimed)
    }

    @Test
    fun `끝점이 허용 거리보다 멀면 닫히지 않음과 시작점-끝점 거리를 준다`() {
        val result = calculator.calculate(TestPaths.openCircle(60.0, gapMeters = 55.0))

        assertTrue("$result", result is TerritoryResult.NotClosed)
        result as TerritoryResult.NotClosed
        assertEquals(55.0, result.gapMeters, 1.0)
        assertEquals(50.0, result.toleranceMeters, 0.0)
    }

    @Test
    fun `반원은 닫히지 않는다`() {
        val result = calculator.calculate(TestPaths.semicircle(60.0))

        assertTrue("$result", result is TerritoryResult.NotClosed)
    }

    @Test
    fun `최소 면적보다 작은 원은 너무 작음과 면적을 준다`() {
        val result = calculator.calculate(TestPaths.circle(30.0))

        assertTrue("$result", result is TerritoryResult.TooSmall)
        result as TerritoryResult.TooSmall
        assertNear(PI * 30 * 30, result.areaSquareMeters, 0.01)
        assertEquals(5_000.0, result.minAreaSquareMeters, 0.0)
    }

    @Test
    fun `최소 면적은 설정으로 바꿀 수 있다`() {
        val result = TerritoryCalculator(TerritoryConfig(minAreaSquareMeters = 1_000.0))
            .calculate(TestPaths.circle(30.0))

        assertTrue("$result", result is TerritoryResult.Claimed)
    }

    @Test
    fun `직선을 왕복하면 둘러싼 영역이 없어 면적 0으로 너무 작다`() {
        val result = calculator.calculate(TestPaths.straightOutAndBack(200.0))

        assertTrue("$result", result is TerritoryResult.TooSmall)
        assertEquals(0.0, (result as TerritoryResult.TooSmall).areaSquareMeters, 1.0)
    }

    @Test
    fun `8자로 자기 교차하면 큰 쪽 고리만 차지한다`() {
        // 고리 면적은 폭×높이×2/3: 오른쪽 8000m², 왼쪽 4000m²
        val result =
            claimed(TestPaths.figureEight(rightWidth = 120.0, leftWidth = 60.0, height = 100.0))

        assertNear(8_000.0, result.areaSquareMeters, 0.02)
    }

    @Test
    fun `같은 원을 두 바퀴 돌아도 한 바퀴 면적만 차지한다`() {
        val result = claimed(TestPaths.circle(60.0, turns = 2.0))

        assertNear(PI * 60 * 60, result.areaSquareMeters, 0.01)
    }

    @Test
    fun `위치 잡음이 있는 두 바퀴도 한 바퀴 면적에 가깝다`() {
        val result = claimed(TestPaths.circle(60.0, turns = 2.0, noiseSigma = 1.0))

        assertNear(PI * 60 * 60, result.areaSquareMeters, 0.1)
    }

    @Test
    fun `경로 중간의 작은 고리는 영역에 넣지 않는다`() {
        val result = claimed(TestPaths.circleWithSmallLoop(radius = 60.0, loopRadius = 15.0))

        assertNear(PI * 60 * 60, result.areaSquareMeters, 0.01)
    }

    @Test
    fun `같은 구간을 갔다가 돌아온 곁가지는 영역을 넓히지 않는다`() {
        val result = claimed(TestPaths.circleWithSpur(radius = 60.0, spurLength = 40.0))

        assertNear(PI * 60 * 60, result.areaSquareMeters, 0.01)
        assertNear(2 * PI * 60, result.perimeterMeters, 0.01)
    }

    @Test
    fun `위치값이 최소 개수보다 적으면 판정 불가다`() {
        val result = calculator.calculate(TestPaths.square(100.0).take(9))

        assertEquals(TerritoryResult.InsufficientPoints, result)
    }

    @Test
    fun `한 자리에서 기록하면 서로 다른 지점이 부족해 판정 불가다`() {
        assertEquals(
            TerritoryResult.InsufficientPoints,
            calculator.calculate(TestPaths.stationary(30))
        )
    }

    @Test
    fun `범위를 벗어난 위치값은 버린다`() {
        val path = TestPaths.circle(60.0)
        val noisy = path.take(10) + GeoPoint(Double.NaN, 0.0) + GeoPoint(91.0, 0.0) + path.drop(10)

        assertEquals(claimed(path).areaSquareMeters, claimed(noisy).areaSquareMeters, 1e-6)
    }

    private fun simulated(type: SimulatedRouteType) = calculator.calculate(
        SimulatedRouteGenerator.generate(type).map { GeoPoint(it.latitude, it.longitude) }
    )

    @Test
    fun `모의 원 경로는 반지름 50m 원 넓이만큼 차지한다`() {
        val result = simulated(SimulatedRouteType.CIRCLE)

        assertTrue("$result", result is TerritoryResult.Claimed)
        assertNear(PI * 50 * 50, (result as TerritoryResult.Claimed).areaSquareMeters, 0.02)
    }

    @Test
    fun `모의 찌그러진 원 경로도 닫힌 영역을 차지한다`() {
        val result = simulated(SimulatedRouteType.SQUISHED_CIRCLE)

        assertTrue("$result", result is TerritoryResult.Claimed)
        assertTrue((result as TerritoryResult.Claimed).areaSquareMeters > 5_000.0)
    }

    @Test
    fun `모의 직선 왕복 경로는 닫히지만 면적이 없어 너무 작다`() {
        val result = simulated(SimulatedRouteType.LINE)

        assertTrue("$result", result is TerritoryResult.TooSmall)
        assertEquals(0.0, (result as TerritoryResult.TooSmall).areaSquareMeters, 1.0)
    }

    @Test
    fun `모의 정지 경로는 판정 불가다`() {
        assertEquals(TerritoryResult.InsufficientPoints, simulated(SimulatedRouteType.STATIONARY))
    }
}
