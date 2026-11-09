package kr.roadcanvas.app.test

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** 화면이 없으므로 땅따먹기 판정 로직을 직접 호출해 규칙을 확인한다. */
class TerritorySteps {
    private val calculator = TerritoryCalculator()
    private lateinit var path: List<GeoPoint>
    private lateinit var result: TerritoryResult

    @Given("한 변 {int}m인 정사각형을 1바퀴 주행했다")
    fun droveSquare(side: Int) {
        path = TestPaths.square(side.toDouble())
    }

    @Given("반지름 {int}m인 원을 돌다가 출발점을 {int}m 남기고 멈췄다")
    fun droveOpenCircle(radius: Int, gap: Int) {
        path = TestPaths.openCircle(radius.toDouble(), gap.toDouble())
    }

    @Given("반지름 {int}m인 땅따먹기 원을 1바퀴 주행했다")
    fun droveCircle(radius: Int) {
        path = TestPaths.circle(radius.toDouble())
    }

    @Given("반지름 {int}m인 원을 같은 길로 {int}바퀴 주행했다")
    fun droveCircleTurns(radius: Int, turns: Int) {
        path = TestPaths.circle(radius.toDouble(), turns = turns.toDouble())
    }

    @Given("{int}m 직선을 땅따먹기로 왕복했다")
    fun droveOutAndBack(length: Int) {
        path = TestPaths.straightOutAndBack(length.toDouble())
    }

    @Given("면적 {int}m²와 {int}m²인 고리로 8자를 주행했다")
    fun droveFigureEight(rightArea: Int, leftArea: Int) {
        // 고리 면적은 폭×높이×2/3이다.
        val height = 100.0
        path = TestPaths.figureEight(
            rightWidth = rightArea * 1.5 / height,
            leftWidth = leftArea * 1.5 / height,
            height = height
        )
    }

    @Given("땅따먹기 위치값 {int}개만 기록했다")
    fun recordedFewPoints(count: Int) {
        path = TestPaths.square(100.0).take(count)
    }

    @When("땅따먹기 영역을 판정한다")
    fun judge() {
        result = calculator.calculate(path)
    }

    @Then("영역을 차지한다")
    fun assertClaimed() {
        assertTrue("$result", result is TerritoryResult.Claimed)
    }

    @Then("면적 {int}m²인 영역을 차지한다")
    fun assertClaimedArea(area: Int) {
        assertEquals(area.toDouble(), claimed().areaSquareMeters, area * 0.01)
    }

    @Then("면적 약 {int}m²인 영역을 차지한다")
    fun assertClaimedAreaAbout(area: Int) {
        assertEquals(area.toDouble(), claimed().areaSquareMeters, area * 0.02)
    }

    @Then("영역의 둘레는 {int}m다")
    fun assertPerimeter(perimeter: Int) {
        assertEquals(perimeter.toDouble(), claimed().perimeterMeters, perimeter * 0.01)
    }

    @Then("경로가 닫히지 않았고 시작점과 끝점 거리는 {int}m다")
    fun assertNotClosed(gap: Int) {
        val notClosed = result as? TerritoryResult.NotClosed
        assertTrue("$result", notClosed != null)
        assertEquals(gap.toDouble(), notClosed!!.gapMeters, 1.0)
    }

    @Then("영역이 너무 작고 면적은 약 {int}m²다")
    fun assertTooSmall(area: Int) {
        val tooSmall = result as? TerritoryResult.TooSmall
        assertTrue("$result", tooSmall != null)
        assertEquals(area.toDouble(), tooSmall!!.areaSquareMeters, maxOf(1.0, area * 0.02))
    }

    @Then("영역을 판정할 수 없다")
    fun assertInsufficient() {
        assertEquals(TerritoryResult.InsufficientPoints, result)
    }

    private fun claimed(): TerritoryResult.Claimed {
        assertTrue("$result", result is TerritoryResult.Claimed)
        return result as TerritoryResult.Claimed
    }
}
