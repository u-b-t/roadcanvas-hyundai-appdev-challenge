package kr.roadcanvas.app.test

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.challengeResultOf
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

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

    @Given("반지름 {int}m 반원을 기록하고 종료했다")
    fun finishSemicircle(radius: Int) {
        progressSteps.finishSavedChallenge(TestPaths.semicircle(radius.toDouble()).toRoutePoints())
    }

    @Given("반지름 {int}m 원을 돌다가 출발점 {int}m 앞에서 종료했다")
    fun finishOpenCircle(radius: Int, gap: Int) {
        progressSteps.finishSavedChallenge(
            TestPaths.openCircle(radius.toDouble(), gap.toDouble()).toRoutePoints()
        )
    }

    @Given("점수 {int}점인 완료 기록이 있다")
    fun savePreviousRecord(score: Int) {
        runBlocking {
            fakeContainer.completedDrawingRepository.save(
                CompletedDrawing(
                    mode = DrawingMode.CIRCLE,
                    // 이번 도전보다 하루 전에 완료한 기록
                    completedAtMillis = System.currentTimeMillis() - DAY_MILLIS,
                    path = emptyList(),
                    score = score,
                    metrics = emptyMap(),
                    distanceMeters = 0.0,
                    durationMillis = 0L
                )
            )
        }
    }

    /** 결과 내용은 화면 높이를 넘으면 스크롤되므로 해당 항목까지 스크롤한 뒤 보이는지 확인한다. */
    @Then("결과에 {string}가 표시된다")
    @Then("결과에 {string}이 표시된다")
    fun assertResultTextDisplayed(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
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

    @Given("반지름 {int}m 원을 기록하고 종료해 완료 기록까지 저장했지만 진행 중 기록이 남아 있다")
    fun finishAndSaveWithoutClearing(radius: Int) {
        val path = TestPaths.circle(radius.toDouble()).toRoutePoints()
        val startedAtMillis = path.first().timeMillis
        val endedAtMillis = path.last().timeMillis + 1_000L
        runBlocking {
            val inProgress = fakeContainer.inProgressDrawingRepository
            inProgress.start(DrawingMode.CIRCLE, startedAtMillis)
            inProgress.appendPoints(path)
            inProgress.finish(endedAtMillis)
            val result =
                challengeResultOf(DrawingMode.CIRCLE, path, startedAtMillis, endedAtMillis)
            fakeContainer.completedDrawingRepository.save(result.toCompletedDrawing())
        }
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
