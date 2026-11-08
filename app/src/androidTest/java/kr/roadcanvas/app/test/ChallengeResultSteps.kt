package kr.roadcanvas.app.test

import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 결과 화면 시나리오. 경로는 진행 중 기록으로 저장해 두고 앱을 다시 실행해 종료한다. */
class ChallengeResultSteps(
    private val ruleHolder: ComposeRuleHolder,
    private val progressSteps: ChallengeProgressSteps
) {
    private val composeRule get() = ruleHolder.composeRule
    private val application = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
    private val fakeContainer get() = application.container as FakeAppContainer

    @Given("반지름 {int}m 원을 기록하고 종료했다")
    fun finishCircle(radius: Int) {
        progressSteps.finishSavedChallenge(TestPaths.circle(radius.toDouble()).toRoutePoints())
    }

    @Given("{int}m 직선을 왕복 기록하고 종료했다")
    fun finishStraightLine(length: Int) {
        progressSteps.finishSavedChallenge(
            TestPaths.straightOutAndBack(length.toDouble()).toRoutePoints()
        )
    }

    @Given("한 자리에서 위치 {int}개를 기록하고 종료했다")
    fun finishStationary(count: Int) {
        progressSteps.finishSavedChallenge(TestPaths.stationary(count).toRoutePoints())
    }

    @Given("완료 기록 저장이 실패하도록 설정되어 있다")
    fun failCompletedStorage() {
        fakeContainer.completedDrawingRepository.failure = IllegalStateException("테스트 저장소 오류")
    }

    @When("완료 기록 저장이 다시 성공하도록 되돌린다")
    fun restoreCompletedStorage() {
        fakeContainer.completedDrawingRepository.failure = null
    }

    @Then("완료 기록 {int}개가 저장되어 있고 점수는 {int}점 이상이다")
    fun assertSavedScoreAtLeast(count: Int, minScore: Int) {
        waitForSummaries(count)
        val scores = summaries().map { it.score }
        check(scores.all { it >= minScore }) { "저장된 점수: $scores" }
    }

    @Then("완료 기록 {int}개가 저장되어 있고 점수는 {int}점 이하다")
    fun assertSavedScoreAtMost(count: Int, maxScore: Int) {
        waitForSummaries(count)
        val scores = summaries().map { it.score }
        check(scores.all { it <= maxScore }) { "저장된 점수: $scores" }
    }

    private fun waitForSummaries(count: Int) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) { summaries().size == count }
        // 한 번만 저장되는지 확인하기 위해 잠시 뒤에도 개수가 같은지 본다.
        composeRule.waitForIdle()
        check(summaries().size == count) { "완료 기록 수: ${summaries().size}" }
    }

    private fun summaries(): List<CompletedDrawingSummary> = runBlocking {
        val result = fakeContainer.completedDrawingRepository.summaries()
        (result as StorageResult.Success).value
    }

    private fun List<GeoPoint>.toRoutePoints(): List<RoutePoint> {
        val start = System.currentTimeMillis() - size * 1_000L
        return mapIndexed { i, it -> RoutePoint(it.latitude, it.longitude, start + i * 1_000L) }
    }
}
