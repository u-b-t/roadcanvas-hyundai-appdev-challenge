package kr.roadcanvas.app.test

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.abs
import kr.roadcanvas.app.score.CircleScoreCalculator
import kr.roadcanvas.app.score.CircleScoreResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.score.ZeroScoreReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** 화면이 없으므로 점수 계산 로직을 직접 호출해 규칙을 확인한다. */
class CircleScoreSteps {
    private val calculator = CircleScoreCalculator()
    private lateinit var path: List<GeoPoint>
    private lateinit var result: CircleScoreResult
    private var comparedScore = 0

    @Given("반지름 {int}m인 원을 1바퀴 주행했다")
    fun droveCircle(radius: Int) {
        path = TestPaths.circle(radius.toDouble())
    }

    @Given("반지름 {int}m인 원을 {double}바퀴만큼 주행했다")
    fun droveCircleTurns(radius: Int, turns: Double) {
        path = TestPaths.circle(radius.toDouble(), turns = turns)
    }

    @Given("반지름 {int}m인 원을 돌다가 출발점 {int}m 앞에서 멈췄다")
    fun droveOpenCircle(radius: Int, gap: Int) {
        path = TestPaths.openCircle(radius.toDouble(), gap.toDouble())
    }

    @Given("반지름 {int}m인 원을 위치 잡음 {int}m로 1바퀴 주행했다")
    fun droveNoisyCircle(radius: Int, sigma: Int) {
        path = TestPaths.circle(radius.toDouble(), noiseSigma = sigma.toDouble())
    }

    @Given("반지름 {int}m인 원을 시계 방향으로 1바퀴 주행했다")
    fun droveClockwiseCircle(radius: Int) {
        path = TestPaths.circle(radius.toDouble(), clockwise = true)
    }

    @Given("반지름 {int}m인 반원을 주행했다")
    fun droveSemicircle(radius: Int) {
        path = TestPaths.semicircle(radius.toDouble())
    }

    @Given("{int}m 직선을 왕복했다")
    fun droveStraightOutAndBack(length: Int) {
        path = TestPaths.straightOutAndBack(length.toDouble())
    }

    @Given("한 자리에서 위치값 {int}개를 기록했다")
    fun stayedStill(count: Int) {
        path = TestPaths.stationary(count)
    }

    @Given("위치값 {int}개만 기록했다")
    fun recordedFewPoints(count: Int) {
        path = TestPaths.circle(20.0).take(count)
    }

    @When("경로 점수를 계산한다")
    fun calculateScore() {
        result = calculator.calculate(path)
    }

    @When("반지름 {int}m인 원을 1바퀴 주행한 경로 점수를 계산한다")
    fun calculateCircleScore(radius: Int) {
        comparedScore = calculator.calculate(TestPaths.circle(radius.toDouble())).score
    }

    @When("반지름 {int}m인 원을 반시계 방향으로 1바퀴 주행한 경로 점수를 계산한다")
    fun calculateCounterClockwiseScore(radius: Int) {
        comparedScore = calculator.calculate(TestPaths.circle(radius.toDouble())).score
    }

    @Then("점수는 {int}점 이상이다")
    fun assertScoreAtLeast(min: Int) {
        assertTrue("점수 ${result.score}", result.score >= min)
    }

    @Then("점수는 {int}점 이하다")
    fun assertScoreAtMost(max: Int) {
        assertTrue("점수 ${result.score}", result.score <= max)
    }

    @Then("두 점수의 차이는 {int}점 이하다")
    fun assertScoreDifferenceAtMost(max: Int) {
        assertTrue("${result.score}, $comparedScore", abs(result.score - comparedScore) <= max)
    }

    @Then("두 점수는 같다")
    fun assertScoresEqual() {
        assertEquals(result.score, comparedScore)
    }

    @Then("점수는 0점이고 사유는 {string}이다")
    fun assertZeroScore(reason: String) {
        assertEquals(0, result.score)
        assertEquals(REASONS.getValue(reason), result.zeroReason)
    }

    private companion object {
        val REASONS = mapOf(
            "이동 없음" to ZeroScoreReason.NO_MOVEMENT,
            "위치값 부족" to ZeroScoreReason.INSUFFICIENT_POINTS,
            "최소 크기 미달" to ZeroScoreReason.TOO_SMALL,
            "닫히지 않음" to ZeroScoreReason.NOT_CLOSED,
            "한 바퀴 미완성" to ZeroScoreReason.INCOMPLETE_TURN
        )
    }
}
